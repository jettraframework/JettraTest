package io.jettra.test.runner;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import io.jettra.report.exporter.SurefireReporter;

/**
 * High-performance, Java 25+ test runner engine for JettraTest.
 * Supports Virtual Threads, @TempDir lifecycle injection,
 * @ParameterizedTest execution with @EnumSource, @ValueSource, @MethodSource,
 * and comprehensive Surefire report generation.
 */
public class JettraTestRunner {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        if (Boolean.getBoolean("skipTests") || Boolean.getBoolean("maven.test.skip")
                || "true".equals(System.getProperty("skipTests")) || "true".equals(System.getProperty("maven.test.skip"))) {
            System.out.println(ANSI_YELLOW + "Tests are skipped." + ANSI_RESET);
            return;
        }

        if (args.length == 0) {
            System.err.println(ANSI_RED + "Usage: JettraTestRunner <test-classes-dir> [classes-dir...]" + ANSI_RESET);
            System.exit(1);
        }

        String testClassesDir = args[0];
        String targetDir = new File(testClassesDir).getParent(); // Usually "target"

        System.out.println(ANSI_CYAN + "-------------------------------------------------------");
        System.out.println(" T E S T S  (JettraTest Modern Framework - Java 25+)");
        System.out.println("-------------------------------------------------------" + ANSI_RESET);

        try {
            List<URL> urls = new ArrayList<>();
            for (String arg : args) {
                urls.add(new File(arg).toURI().toURL());
            }

            URLClassLoader classLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]), JettraTestRunner.class.getClassLoader());
            List<Class<?>> classes = findClasses(new File(testClassesDir), testClassesDir, classLoader);

            // Phase 1: Determine Server Requirements and Launcher
            boolean requiresServer = false;
            Class<?> launcherClass = null;

            for (Class<?> clazz : classes) {
                if (hasAnnotation(clazz, "JettraTestLauncher")) {
                    launcherClass = clazz;
                }

                boolean hasTest = false;
                for (Method m : clazz.getDeclaredMethods()) {
                    if (isTestMethod(m)) {
                        hasTest = true;
                        break;
                    }
                }
                if (hasTest && !hasAnnotation(clazz, "NotRequiresRunningServer")) {
                    requiresServer = true;
                }
            }

            // Phase 2: Start Server if required
            int testPort = 0;
            Object launcherInstance = null;
            if (requiresServer) {
                try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                    testPort = socket.getLocalPort();
                } catch (Exception e) {
                    testPort = 9002;
                }

                if (launcherClass != null) {
                    System.out.println(ANSI_CYAN + "[JettraTestRunner] Server required. Starting via " + launcherClass.getName() + " on port " + testPort + ANSI_RESET);
                    try {
                        launcherInstance = launcherClass.getDeclaredConstructor().newInstance();
                        Method startMethod = launcherClass.getMethod("startServer", int.class);
                        startMethod.invoke(launcherInstance, testPort);
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        System.err.println(ANSI_RED + "[JettraTestRunner] Failed to start server using launcher: " + e.getMessage() + ANSI_RESET);
                        e.printStackTrace();
                    }
                } else {
                    System.err.println(ANSI_YELLOW + "[JettraTestRunner] [WARNING] Server is required by tests, but no class annotated with @JettraTestLauncher was found!" + ANSI_RESET);
                }
            }

            int totalTests = 0;
            int totalFailures = 0;

            for (Class<?> clazz : classes) {
                List<Method> testMethods = new ArrayList<>();
                for (Method m : clazz.getDeclaredMethods()) {
                    if (isTestMethod(m)) {
                        testMethods.add(m);
                    }
                }

                if (testMethods.isEmpty()) {
                    continue;
                }

                int classTests = 0;
                int classFailures = 0;
                StringBuilder failureDetails = new StringBuilder();
                long startTime = System.currentTimeMillis();

                List<Path> staticTempDirs = injectStaticTempDirs(clazz);
                try {
                    invokeStaticLifecycleMethods(clazz, "BeforeAll");

                    for (Method method : testMethods) {
                        List<InvocationPlan> plans = buildInvocationPlans(clazz, method);

                        for (InvocationPlan plan : plans) {
                            classTests++;
                            totalTests++;

                            final int currentPort = testPort;
                            final boolean serverNeeded = requiresServer;
                            final AtomicReference<Throwable> failureRef = new AtomicReference<>();

                            // Execute test on a dedicated Java 25 Virtual Thread
                            Thread vThread = Thread.ofVirtual().name("jettra-test-" + method.getName(), 0).start(() -> {
                                Object instance = null;
                                List<Path> instanceTempDirs = null;
                                try {
                                    Constructor<?> ctor = clazz.getDeclaredConstructor();
                                    ctor.setAccessible(true);
                                    instance = ctor.newInstance();

                                    // Inject dynamic server port if available
                                    if (serverNeeded && currentPort > 0) {
                                        injectServerPort(instance, currentPort);
                                    }

                                    // Inject dependencies (@Inject)
                                    injectDependencies(instance);

                                    // Inject instance fields annotated with @TempDir
                                    instanceTempDirs = injectInstanceTempDirs(instance, clazz);

                                    // Execute @BeforeEach
                                    invokeLifecycleMethods(clazz, instance, "BeforeEach");

                                    // Invoke method
                                    method.setAccessible(true);
                                    method.invoke(instance, plan.arguments);

                                } catch (Throwable t) {
                                    Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null)
                                            ? ite.getCause()
                                            : t;
                                    failureRef.set(cause);
                                } finally {
                                    if (instance != null) {
                                        try {
                                            invokeLifecycleMethods(clazz, instance, "AfterEach");
                                        } catch (Throwable t) {
                                            if (failureRef.get() == null) {
                                                failureRef.set(t);
                                            }
                                        }
                                    }
                                    if (instanceTempDirs != null) {
                                        cleanupTempDirs(instanceTempDirs);
                                    }
                                    if (plan.tempDirsToClean != null) {
                                        cleanupTempDirs(plan.tempDirsToClean);
                                    }
                                }
                            });

                            vThread.join();

                            if (failureRef.get() != null) {
                                classFailures++;
                                totalFailures++;
                                Throwable t = failureRef.get();
                                String errorMessage = t.toString();
                                String testDesc = plan.displayName != null ? plan.displayName : method.getName();
                                failureDetails.append(testDesc).append(" failed: ").append(errorMessage).append("\n");

                                System.err.println(ANSI_RED + "  <<< FAILURE! -- in " + clazz.getName());
                                System.err.println("      Test: " + testDesc);
                                System.err.println("      Reason: " + errorMessage + ANSI_RESET);
                                if (!(t instanceof AssertionError)) {
                                    t.printStackTrace();
                                }
                            }
                        }
                    }
                } finally {
                    try {
                        invokeStaticLifecycleMethods(clazz, "AfterAll");
                    } finally {
                        cleanupTempDirs(staticTempDirs);
                    }
                }

                if (classTests > 0) {
                    long endTime = System.currentTimeMillis();
                    double timeSec = (endTime - startTime) / 1000.0;
                    System.out.printf("Running %s%n", clazz.getName());
                    String resultColor = classFailures > 0 ? ANSI_RED : ANSI_GREEN;
                    System.out.printf(resultColor + "Tests run: %d, Failures: %d, Errors: 0, Skipped: 0, Time elapsed: %.3f s%n" + ANSI_RESET,
                            classTests, classFailures, timeSec);

                    SurefireReporter.writeReport(targetDir, clazz.getName(), classTests, classFailures, 0, 0, timeSec, failureDetails.toString());
                }
            }

            System.out.println("\nResults:\n");
            String totalColor = totalFailures > 0 ? ANSI_RED : ANSI_GREEN;
            System.out.printf(totalColor + "Tests run: %d, Failures: %d, Errors: 0, Skipped: 0%n" + ANSI_RESET, totalTests, totalFailures);

            // Phase 3: Stop server
            if (launcherInstance != null) {
                System.out.println(ANSI_CYAN + "[JettraTestRunner] Stopping server via " + launcherClass.getName() + ANSI_RESET);
                try {
                    Method stopMethod = launcherClass.getMethod("stopServer");
                    stopMethod.invoke(launcherInstance);
                } catch (Exception e) {
                    System.err.println(ANSI_RED + "[JettraTestRunner] Failed to stop server: " + e.getMessage() + ANSI_RESET);
                }
            }

            if (totalFailures > 0) {
                System.out.println("\n" + ANSI_RED + "[ERROR] There are test failures." + ANSI_RESET);
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // Invocation Planning & Parameter Source Resolution
    // =========================================================================

    private static class InvocationPlan {
        String displayName;
        Object[] arguments;
        List<Path> tempDirsToClean;

        InvocationPlan(String displayName, Object[] arguments, List<Path> tempDirsToClean) {
            this.displayName = displayName;
            this.arguments = arguments;
            this.tempDirsToClean = tempDirsToClean;
        }
    }

    private static List<InvocationPlan> buildInvocationPlans(Class<?> clazz, Method method) throws Exception {
        List<InvocationPlan> plans = new ArrayList<>();
        String baseDisplayName = getDisplayName(method);

        boolean isParameterized = hasAnnotation(method, "ParameterizedTest");
        if (!isParameterized) {
            // Standard test
            List<Path> methodTempDirs = new ArrayList<>();
            Object[] args = resolveMethodParameters(method, methodTempDirs);
            plans.add(new InvocationPlan(baseDisplayName, args, methodTempDirs));
            return plans;
        }

        // Parameterized Test: Extract arguments from sources
        List<Object[]> argumentSets = extractArgumentSets(clazz, method);

        if (argumentSets.isEmpty()) {
            // Fallback to parameter resolution if no source found
            List<Path> methodTempDirs = new ArrayList<>();
            Object[] args = resolveMethodParameters(method, methodTempDirs);
            plans.add(new InvocationPlan(baseDisplayName, args, methodTempDirs));
            return plans;
        }

        int index = 1;
        for (Object[] rawArgs : argumentSets) {
            List<Path> invocationTempDirs = new ArrayList<>();
            Object[] fullArgs = alignArgumentsWithParameters(method, rawArgs, invocationTempDirs);
            String runName = baseDisplayName + " [" + index + "] " + Arrays.toString(rawArgs);
            plans.add(new InvocationPlan(runName, fullArgs, invocationTempDirs));
            index++;
        }

        return plans;
    }

    private static List<Object[]> extractArgumentSets(Class<?> clazz, Method method) throws Exception {
        List<Object[]> sets = new ArrayList<>();

        for (Annotation ann : method.getAnnotations()) {
            String name = ann.annotationType().getSimpleName();

            if ("EnumSource".equals(name)) {
                Class<?> enumClass = (Class<?>) ann.annotationType().getMethod("value").invoke(ann);
                Object[] constants = enumClass.getEnumConstants();
                String[] names = (String[]) ann.annotationType().getMethod("names").invoke(ann);
                String mode = "INCLUDE";
                try {
                    Object m = ann.annotationType().getMethod("mode").invoke(ann);
                    if (m != null) mode = m.toString();
                } catch (Exception ignored) {}

                Set<String> nameFilter = (names != null && names.length > 0) ? new HashSet<>(Arrays.asList(names)) : Collections.emptySet();
                if (constants != null) {
                    for (Object c : constants) {
                        String cName = ((Enum<?>) c).name();
                        if (nameFilter.isEmpty()) {
                            sets.add(new Object[]{c});
                        } else if ("EXCLUDE".equalsIgnoreCase(mode)) {
                            if (!nameFilter.contains(cName)) sets.add(new Object[]{c});
                        } else {
                            if (nameFilter.contains(cName)) sets.add(new Object[]{c});
                        }
                    }
                }
            } else if ("ValueSource".equals(name)) {
                String[] strings = (String[]) ann.annotationType().getMethod("strings").invoke(ann);
                if (strings != null && strings.length > 0) {
                    for (String s : strings) sets.add(new Object[]{s});
                }
                int[] ints = (int[]) ann.annotationType().getMethod("ints").invoke(ann);
                if (ints != null && ints.length > 0) {
                    for (int i : ints) sets.add(new Object[]{i});
                }
                long[] longs = (long[]) ann.annotationType().getMethod("longs").invoke(ann);
                if (longs != null && longs.length > 0) {
                    for (long l : longs) sets.add(new Object[]{l});
                }
                double[] doubles = (double[]) ann.annotationType().getMethod("doubles").invoke(ann);
                if (doubles != null && doubles.length > 0) {
                    for (double d : doubles) sets.add(new Object[]{d});
                }
                boolean[] booleans = (boolean[]) ann.annotationType().getMethod("booleans").invoke(ann);
                if (booleans != null && booleans.length > 0) {
                    for (boolean b : booleans) sets.add(new Object[]{b});
                }
            } else if ("MethodSource".equals(name)) {
                String[] methodNames = (String[]) ann.annotationType().getMethod("value").invoke(ann);
                if (methodNames == null || methodNames.length == 0 || (methodNames.length == 1 && methodNames[0].isBlank())) {
                    methodNames = new String[]{method.getName()};
                }
                for (String mName : methodNames) {
                    Method provider = clazz.getDeclaredMethod(mName);
                    provider.setAccessible(true);
                    Object result = provider.invoke(null);
                    if (result instanceof Iterable<?> iterable) {
                        for (Object o : iterable) {
                            sets.add((o instanceof Object[] oa) ? oa : new Object[]{o});
                        }
                    }
                }
            }
        }

        return sets;
    }

    private static Object[] alignArgumentsWithParameters(Method method, Object[] rawArgs, List<Path> tempDirs) throws IOException {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        int rawIdx = 0;

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (hasAnnotation(p, "TempDir")) {
                Path temp = Files.createTempDirectory("jettra_temp_param_");
                tempDirs.add(temp);
                result[i] = (p.getType() == File.class) ? temp.toFile() : temp;
            } else if (rawIdx < rawArgs.length) {
                result[i] = rawArgs[rawIdx++];
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private static Object[] resolveMethodParameters(Method method, List<Path> tempDirs) throws IOException {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (hasAnnotation(p, "TempDir")) {
                Path temp = Files.createTempDirectory("jettra_temp_param_");
                tempDirs.add(temp);
                args[i] = (p.getType() == File.class) ? temp.toFile() : temp;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    // =========================================================================
    // TempDir Provisioning and Lifecycle Cleanup
    // =========================================================================

    private static List<Path> injectStaticTempDirs(Class<?> clazz) {
        List<Path> created = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && hasAnnotation(field, "TempDir")) {
                    try {
                        field.setAccessible(true);
                        Path temp = Files.createTempDirectory("jettra_temp_static_");
                        created.add(temp);
                        if (field.getType() == File.class) {
                            field.set(null, temp.toFile());
                        } else {
                            field.set(null, temp);
                        }
                    } catch (Exception e) {
                        System.err.println("[JettraTestRunner] Error setting static @TempDir on " + field.getName() + ": " + e.getMessage());
                    }
                }
            }
            current = current.getSuperclass();
        }
        return created;
    }

    private static List<Path> injectInstanceTempDirs(Object instance, Class<?> clazz) {
        List<Path> created = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && hasAnnotation(field, "TempDir")) {
                    try {
                        field.setAccessible(true);
                        Path temp = Files.createTempDirectory("jettra_temp_inst_");
                        created.add(temp);
                        if (field.getType() == File.class) {
                            field.set(instance, temp.toFile());
                        } else {
                            field.set(instance, temp);
                        }
                    } catch (Exception e) {
                        System.err.println("[JettraTestRunner] Error setting @TempDir on " + field.getName() + ": " + e.getMessage());
                    }
                }
            }
            current = current.getSuperclass();
        }
        return created;
    }

    private static void cleanupTempDirs(List<Path> dirs) {
        if (dirs == null) return;
        for (Path dir : dirs) {
            if (dir != null && Files.exists(dir)) {
                try {
                    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.deleteIfExists(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                            Files.deleteIfExists(d);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }
    }

    // =========================================================================
    // Reflection & Test Method Helpers
    // =========================================================================

    private static boolean isTestMethod(Method m) {
        if (m == null) return false;
        for (Annotation ann : m.getAnnotations()) {
            String name = ann.annotationType().getSimpleName();
            if ("Test".equals(name) || "JettraTest".equals(name) || "ParameterizedTest".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(Class<?> clazz, String simpleName) {
        if (clazz == null) return false;
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static boolean hasAnnotation(Method method, String simpleName) {
        if (method == null) return false;
        for (Annotation ann : method.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static boolean hasAnnotation(Field field, String simpleName) {
        if (field == null) return false;
        for (Annotation ann : field.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static boolean hasAnnotation(Parameter param, String simpleName) {
        if (param == null) return false;
        for (Annotation ann : param.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static String getDisplayName(Method method) {
        for (Annotation ann : method.getAnnotations()) {
            if ("DisplayName".equals(ann.annotationType().getSimpleName())) {
                try {
                    return (String) ann.annotationType().getMethod("value").invoke(ann);
                } catch (Exception ignored) {}
            }
        }
        return method.getName() + "()";
    }

    private static void injectServerPort(Object instance, int testPort) {
        for (String fieldName : List.of("ServerPortTest", "serverPortTest", "port", "serverPort")) {
            try {
                Field field = instance.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.getType() == Integer.class || field.getType() == int.class) {
                    field.set(instance, testPort);
                    return;
                } else if (field.getType() == String.class) {
                    field.set(instance, String.valueOf(testPort));
                    return;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                System.err.println("[JettraTestRunner] Could not set server port on " + fieldName + ": " + e.getMessage());
            }
        }
    }

    private static void injectDependencies(Object target) {
        if (target == null) return;
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                boolean hasInject = false;
                for (Annotation ann : field.getAnnotations()) {
                    if (ann.annotationType().getSimpleName().equals("Inject")) {
                        hasInject = true;
                        break;
                    }
                }
                if (hasInject) {
                    try {
                        field.setAccessible(true);
                        if (field.get(target) == null) {
                            Class<?> type = field.getType();
                            Class<?> implClass = type;
                            if (type.isInterface()) {
                                try {
                                    implClass = Class.forName(type.getName() + "Impl");
                                } catch (ClassNotFoundException e) {
                                    System.err.println("[JettraTestRunner] Implementation not found for interface " + type.getName());
                                    continue;
                                }
                            }
                            Object injectedInstance = implClass.getDeclaredConstructor().newInstance();
                            field.set(target, injectedInstance);
                            injectDependencies(injectedInstance);
                        }
                    } catch (Exception e) {
                        System.err.println("[JettraTestRunner] Error injecting dependency into " + field.getName() + ": " + e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void invokeLifecycleMethods(Class<?> clazz, Object instance, String annotationSimpleName) throws Exception {
        if (clazz == null || instance == null) return;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (hasAnnotation(m, annotationSimpleName) && !Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    m.invoke(instance);
                }
            }
            current = current.getSuperclass();
        }
    }

    private static void invokeStaticLifecycleMethods(Class<?> clazz, String annotationSimpleName) {
        if (clazz == null) return;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (hasAnnotation(m, annotationSimpleName) && Modifier.isStatic(m.getModifiers())) {
                    try {
                        m.setAccessible(true);
                        m.invoke(null);
                    } catch (Exception e) {
                        System.err.println("[JettraTestRunner] Error executing @" + annotationSimpleName + " static method " + m.getName() + ": " + e.getMessage());
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    private static List<Class<?>> findClasses(File directory, String rootDir, ClassLoader classLoader) {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    classes.addAll(findClasses(file, rootDir, classLoader));
                } else if (file.getName().endsWith(".class")) {
                    String className = file.getAbsolutePath().replace(rootDir, "").replace(File.separator, ".");
                    if (className.startsWith(".")) {
                        className = className.substring(1);
                    }
                    className = className.replace(".class", "");
                    try {
                        classes.add(classLoader.loadClass(className));
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    }
                }
            }
        }
        return classes;
    }
}

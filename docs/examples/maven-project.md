# Maven Multi-Module Project

This example models a Maven multi-module project source tree stored in S3.
It demonstrates navigating between modules, accessing named files like `pom.xml`,
and using `@Parent` to walk back up the directory hierarchy.

## S3 Bucket Structure

```text
my-project/
  pom.xml
  README.md
  core/
    pom.xml
    src/
      main/
        java/
          App.java
          Config.java
        resources/
          application.properties
      test/
        java/
          AppTest.java
        resources/
          test.properties
  web/
    pom.xml
    src/
      main/
        java/
          WebApp.java
          Controller.java
        resources/
          templates/
            index.html
      test/
        java/
          WebAppTest.java
```

## Interfaces

```java
// Root project — contains the parent pom and modules
public interface Project extends S3.Dir {

    @Name("pom.xml")
    PomFile pomXml();

    @Name("README.md")
    ReadmeFile readme();

    // List all modules as directories
    Stream<Module> modules();

    // Jump directly to a module by name
    Module module(String name);
}

// A single module (core, web, etc.)
public interface Module extends S3.Dir {

    @Name("pom.xml")
    PomFile pomXml();

    Src src();

    // Navigate back to the project root
    @Parent
    Project project();
}

// The src/ directory splits into main and test
public interface Src extends S3.Dir {
    Section main();
    Section test();
}

// A section (main or test) contains java and resources
public interface Section extends S3.Dir {
    SourceDir java();
    SourceDir resources();

    // Navigate back to the module (src → module)
    @Parent(2)
    Module module();
}

// A directory of source files
public interface SourceDir extends S3.Dir {
    Stream<SourceFile> sources();
}

// An individual source file
public interface SourceFile extends S3.File {
}

// pom.xml — readable as a string
public interface PomFile extends S3.File {
}

// README.md — readable as a string
public interface ReadmeFile extends S3.File {
}
```

## Usage

### List all modules

```java
Project project = bucket.as(Project.class);

project.modules().forEach(module -> {
    System.out.println(module.file().getName());
});
// Output:
//   core
//   web
```

### Read a module's pom.xml

```java
Module core = project.module("core");

String pom = core.pomXml().getValueAsString();
System.out.println(pom);
```

### Navigate from sources back to the project

The `@Parent` annotation lets you walk back up the tree without tracking
references manually:

```java
Module web = project.module("web");
Section main = web.src().main();

// @Parent(2) jumps from section → src → module
Module parent = main.module();
System.out.println(parent.file().getName());
// Output: web

// From the module, @Parent jumps to the project
Project root = parent.project();
System.out.println(root.readme().getValueAsString());
```

### List source files in a module

```java
Module core = project.module("core");

core.src().main().java().sources().forEach(f -> {
    System.out.println(f.file().getAbsoluteName());
});
// Output:
//   my-project/core/src/main/java/App.java
//   my-project/core/src/main/java/Config.java
```

## Features Used

- [`@Name`](../api/annotations/name.md) — maps `pomXml()` to `pom.xml` and `readme()` to `README.md`
- [`@Parent`](../api/annotations/parent.md) — navigates from module back to project, and from section back to module with `@Parent(2)`
- [Named child lookup](../guide/typed-proxies.md#named-child--interface-return-type-with-string-parameter) — `module(String name)` for direct access to a module by name
- [`Stream<Dir>`](../guide/directories-and-files.md#return-type-dispatch-for-directories) — `modules()` returns only directory entries
- [`S3.File`](../api/s3-interfaces.md#s3file) — `PomFile` and `ReadmeFile` extend `S3.File` for content access

## Test

See [MavenProjectTest.java](https://github.com/tomitribe/jaws/blob/master/jaws-s3/src/test/java/org/tomitribe/jaws/s3/examples/MavenProjectTest.java) for a working test of this example.

rootProject.name = "senik-admin"


// this is the way to create a composite build instead of a multi project build
// for reference multi project build should have a parent folder and in the settings file it should include projects like:
// include(:senik)
// include(:common)
// then in build the dependency would be:
// implementation(project(":common"))
// https://stackoverflow.com/questions/60464719/gradle-includebuild-vs-implementation-project
includeBuild("../common")

dependencyResolutionManagement {
    repositories {
        mavenLocal()
    }
    versionCatalogs {
        create("libs") {
            from("gr.alx:versions:1.0")
        }
    }
}

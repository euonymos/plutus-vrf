val scalusVersion = "0.10.1+129-794313e4-SNAPSHOT"

// Latest Scala 3 LTS version
ThisBuild / scalaVersion := "3.3.6"

ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

// Add the Scalus compiler plugin
addCompilerPlugin("org.scalus" %% "scalus-plugin" % scalusVersion)

// Test dependencies
ThisBuild / testFrameworks += new TestFramework("munit.Framework")

// Main application
lazy val core = (project in file("."))
    .settings(
      resolvers +=
          "Sonatype OSS Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/",
      libraryDependencies ++= Seq(
        // Scalus
        "org.scalus" %% "scalus" % scalusVersion,
        "org.scalus" %% "scalus-testkit" % scalusVersion,
        "org.scalus" %% "scalus-bloxbean-cardano-client-lib" % scalusVersion,
        // Cardano Client library
        "com.bloxbean.cardano" % "cardano-client-lib" % "0.6.4",
        "com.bloxbean.cardano" % "cardano-client-backend-blockfrost" % "0.6.4",
        // Tapir for API definition
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.25",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.25",
        // Argument parsing
        "com.monovore" %% "decline" % "2.5.0",
        "org.slf4j" % "slf4j-simple" % "2.0.17"
      ),
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "1.1.0" % Test,
        "org.scalameta" %% "munit-scalacheck" % "1.1.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
      )
    )

import org.gradle.api.publish.maven.MavenPublication
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))


android {
    namespace = "com.ccubas.camera"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }


}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.io.coil)
    implementation(libs.coil.video)
    implementation(libs.androidx.icons)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.bundles.camerax)
    implementation(libs.bundles.media3)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.carloscubas1609"
                artifactId = "compose-camera"
                version = "1.0.0"
                pom {
                    name.set("compose-camera")
                    description.set("Compose Camera")
                    url.set("https://github.com/CarlosCubas1609/compose-camera")
                    licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
                    scm { url.set("https://github.com/CarlosCubas1609/compose-camera") }
                    developers { developer { id.set("CarlosCubas1609"); name.set("Carlos Cubas") } }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/CarlosCubas1609/compose-camera")
                credentials {
                    username = keystoreProperties["GITHUB_ACTOR"] as String? ?: System.getenv("GITHUB_ACTOR")
                    password = keystoreProperties["GITHUB_TOKEN"] as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
/*
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.CarlosCubas1609"
            artifactId = "compose-camera"
            version = "1.0.0"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("compose-camera"); description.set("Compose Camera")
                url.set("https://github.com/CarlosCubas1609/compose-camera")
                licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
                scm { url.set("https://github.com/CarlosCubas1609/compose-camera") }
                developers { developer { id.set("CarlosCubas1609"); name.set("Carlos Cubas") } }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/CarlosCubas1609/compose-camera")
            credentials {
                username = keystoreProperties["GITHUB_ACTOR"] as String? ?: System.getenv("GITHUB_ACTOR")
                password = keystoreProperties["GITHUB_TOKEN"] as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}*/

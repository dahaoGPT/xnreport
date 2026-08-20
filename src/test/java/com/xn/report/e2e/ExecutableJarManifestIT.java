package com.xn.report.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

class ExecutableJarManifestIT {

    @Test
    void packagedJarStartsGenerateReport() throws Exception {
        Path jarPath = Paths.get("target/xnreport-1.0.0-SNAPSHOT.jar")
                .toAbsolutePath().normalize();
        assertThat(jarPath).isRegularFile();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            assertThat(manifest).isNotNull();
            assertThat(manifest.getMainAttributes().getValue("Main-Class"))
                    .isEqualTo("org.springframework.boot.loader.JarLauncher");
            assertThat(manifest.getMainAttributes().getValue("Start-Class"))
                    .isEqualTo("com.xn.report.GenerateReport");
            assertThat(manifest.getMainAttributes().getValue("Build-Jdk-Spec"))
                    .isEqualTo("1.8");
        }
    }
}

package rip.yawn.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ApplicationYamlTest {

    @Test
    void flywayValidationAllowsIntentionalIgnoredCompatibilityMigrations() throws IOException {
        var sources = new YamlPropertySourceLoader().load(
            "application.yml", new ClassPathResource("application.yml"));

        assertThat(sources)
            .extracting(source -> source.getProperty("spring.flyway.ignore-migration-patterns"))
            .containsExactly("*:future,*:ignored");
    }
}

package rip.yawn.agent.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "rip.yawn.agent")
@EnableJpaRepositories(basePackages = "rip.yawn.agent")
public class AgentConfig {
}

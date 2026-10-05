package nl.banksim.api.simulation;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KlokConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock klok() {
        return Clock.system(SimulationClock.ZONE);
    }
}

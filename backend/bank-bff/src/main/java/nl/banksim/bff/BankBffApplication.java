package nl.banksim.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BankBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankBffApplication.class, args);
    }
}

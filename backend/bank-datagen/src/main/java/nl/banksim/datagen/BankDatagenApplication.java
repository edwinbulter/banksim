package nl.banksim.datagen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BankDatagenApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BankDatagenApplication.class, args)));
    }
}

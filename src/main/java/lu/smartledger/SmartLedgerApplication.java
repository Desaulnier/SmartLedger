package lu.smartledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("lu.smartledger.mapper")
public class SmartLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLedgerApplication.class, args);
    }

}

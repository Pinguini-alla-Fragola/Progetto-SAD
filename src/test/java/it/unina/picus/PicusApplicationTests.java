package it.unina.picus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:picus")
class PicusApplicationTests {

    @Test
    void contextLoads() {
    }
}

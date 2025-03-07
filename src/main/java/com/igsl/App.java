package com.igsl;

/**
 * Hello world!
 *
 */
import com.igsl.migration.DataMigration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@MapperScan("com.igsl.migration.mapper")
public class App {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        DataMigration dataMigration = context.getBean(DataMigration.class);
        dataMigration.execute(args);
        // 执行完成后关闭应用
        context.close();
    }
}

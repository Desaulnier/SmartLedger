package lu.smartledger; // 建议放在 test 包下

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/smartledger?serverTimezone=GMT%2B8", "root", "1128lxlLXL@")
                .globalConfig(builder -> {
                    builder.author("lu") // 设置作者名
                            .outputDir("D:/Graduation Project/SmartLedger/src/main/java")
                            .disableOpenDir()
                            // 指定使用 OpenAPI 3 (即 @Schema)
                            .enableSwagger();
                })
                .packageConfig(builder -> {
                    builder.parent("lu.smartledger") // 设置父包名
                            .entity("model.domain")     // 实体类包名
                            .mapper("mapper")           // Mapper 接口包名
                            .service("service")         // Service 包名
                            .serviceImpl("service.impl")
                            .pathInfo(Collections.singletonMap(OutputFile.xml, "D:/Graduation Project/SmartLedger/src/main/resources/mapper"));
                    // ↑ 关键：这行直接把 XML 生成到 resources 目录下
                })
                .strategyConfig(builder -> {
                    builder.addInclude("categories",
                                    "bills") // 填入你想生成的表
                            .entityBuilder()
                            .enableLombok()             // 开启 Lombok
                            .enableChainModel()         // 开启链式模型 @Accessors(chain = true)
                            .idType(IdType.AUTO)        // 主键自增
                            .enableTableFieldAnnotation() // 生成字段注解 @TableField
                            .mapperBuilder()
                            .enableMapperAnnotation()   // 开启 @Mapper 注解
                            .enableBaseResultMap()      // 关键：生成 XML 中的 ResultMap
                            .enableBaseColumnList();    // 关键：生成 XML 中的通用查询列名
                })
                .templateEngine(new FreemarkerTemplateEngine()) // 使用 Freemarker 引擎
                .execute();
    }
}
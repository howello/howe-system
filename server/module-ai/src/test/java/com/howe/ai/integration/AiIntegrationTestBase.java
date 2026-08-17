package com.howe.ai.integration;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 管理员助手集成测试基类：连真实 MySQL 的独立隔离库 howe-ai-itest，绝不触碰业务库 howe-system。
 *
 * <p>连接地址优先读环境变量 {@code AI_ITEST_DB_URL} / {@code AI_ITEST_DB_USERNAME} / {@code AI_ITEST_DB_PASSWORD}，
 * 缺省回落到本机隔离库。数据库不可达时用 {@link Assumptions#assumeTrue(boolean)} 跳过整个测试类——
 * 这是客观环境阻断而非断言被跳过冒充通过；缺失环境会在验收报告里明确记录。</p>
 *
 * <p>建表脚本 {@code ai-itest-schema.sql} 全部是 {@code CREATE TABLE IF NOT EXISTS}，幂等可重复执行；
 * 每个测试方法自行用 {@link SqlSession} 的事务边界控制是否提交，基类不替测试方法回滚，以保证
 * 跨方法、跨会话的可恢复语义（租约恢复、Outbox 重新投递等正是要验证跨事务边界的行为）。</p>
 */
public abstract class AiIntegrationTestBase {

    private static volatile SqlSessionFactory SQL_SESSION_FACTORY;
    private static volatile boolean AVAILABLE;

    /** 子类直接拿工厂开 session；返回 null 表示隔离库不可用（测试应已被 assumeTrue 跳过）。 */
    protected static SqlSessionFactory sessionFactory() {
        return SQL_SESSION_FACTORY;
    }

    protected static boolean integrationDbAvailable() {
        return AVAILABLE;
    }

    @BeforeAll
    static void prepareIsolatedSchema() {
        if (SQL_SESSION_FACTORY != null) return;
        synchronized (AiIntegrationTestBase.class) {
            if (SQL_SESSION_FACTORY != null) return;
            DataSource ds = buildDataSource();
            if (!ping(ds)) {
                AVAILABLE = false;
                Assumptions.assumeTrue(false, "集成测试隔离库 howe-ai-itest 不可达，跳过（环境阻断，非断言失败）");
                return;
            }
            applySchema(ds);
            SQL_SESSION_FACTORY = buildSessionFactory(ds);
            AVAILABLE = true;
        }
    }

    private static DataSource buildDataSource() {
        String url = System.getenv().getOrDefault("AI_ITEST_DB_URL",
            "jdbc:mysql://localhost:3306/howe-ai-itest?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        String user = System.getenv().getOrDefault("AI_ITEST_DB_USERNAME", "howe");
        String password = System.getenv().getOrDefault("AI_ITEST_DB_PASSWORD", "howe");
        return new PooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password);
    }

    private static boolean ping(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            return s.execute("SELECT 1");
        } catch (SQLException e) {
            return false;
        }
    }

    private static void applySchema(DataSource ds) {
        List<String> statements = readSchemaStatements();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String ddl : statements) {
                if (!ddl.isBlank()) s.execute(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("集成测试建表失败：" + e.getMessage(), e);
        }
    }

    private static List<String> readSchemaStatements() {
        try (InputStream in = Objects.requireNonNull(
                AiIntegrationTestBase.class.getResourceAsStream("ai-itest-schema.sql"),
                "建表脚本 ai-itest-schema.sql 不在测试资源里");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.trim().startsWith("--")) continue;
                current.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    statements.add(current.toString().trim().replaceAll(";$", ""));
                    current.setLength(0);
                }
            }
            return statements;
        } catch (IOException e) {
            throw new IllegalStateException("读取建表脚本失败：" + e.getMessage(), e);
        }
    }

    private static SqlSessionFactory buildSessionFactory(DataSource ds) {
        Environment env = new Environment("ai-itest", new JdbcTransactionFactory(), ds);
        Configuration config = new Configuration(env);
        // 与生产一致：mapper 接口与 XML 都在 com.howe.ai.persistence / mapper/ai/*.xml。
        // 先注册接口，再用 XMLMapperBuilder 把 XML 里的 SQL 语句绑定到接口方法上（生产靠 mapperLocations 加载 XML）。
        config.addMappers("com.howe.ai.persistence");
        loadMapperXml(config, "mapper/ai/AiFactMapper.xml");
        loadMapperXml(config, "mapper/ai/AiConfigMapper.xml");
        return new SqlSessionFactoryBuilder().build(config);
    }

    private static void loadMapperXml(Configuration config, String classpathLocation) {
        try (InputStream in = AiIntegrationTestBase.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                Assumptions.assumeTrue(false, "测试 classpath 缺少 " + classpathLocation + "（环境阻断）");
                return;
            }
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, config,
                "resource:" + classpathLocation, config.getSqlFragments()).parse();
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + classpathLocation + " 失败：" + e.getMessage(), e);
        }
    }

    /** 便捷打开一个可提交的 session（自动提交=false，由调用方 commit/rollback/close）。 */
    protected SqlSession openSession() {
        Assumptions.assumeTrue(AVAILABLE, "隔离库不可用");
        return sessionFactory().openSession(false);
    }
}

package com.howe.share.service;

import com.howe.share.domain.ShareLink;
import com.howe.share.mapper.ShareLinkMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 Mapper XML 在 CI 专用 MySQL 验证查看次数和撤销策略。 */
@EnabledIfEnvironmentVariable(named = "HOWE_TEST_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/howe_test.*")
class ShareLinkSqlTest {
    private static SqlSessionFactory sessions;

    /** 只在 howe_test 库重建当前模块测试表，DDL 直接来自模块脚本。 */
    @BeforeAll
    static void prepare() throws Exception {
        var data = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getenv("HOWE_TEST_MYSQL_URL"),
                "root", System.getenv("HOWE_TEST_MYSQL_PASSWORD"));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sql/share.sql"))) root = root.getParent();
        assertNotNull(root);
        String sql = Files.readString(root.resolve("sql/share.sql"));
        int begin = sql.indexOf("create table share_link");
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute("drop table if exists share_link");
            statement.execute(sql.substring(begin, sql.indexOf(';', begin) + 1));
        }
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), data));
        config.getTypeAliasRegistry().registerAlias("ShareLink", ShareLink.class);
        try (var xml = ShareLinkSqlTest.class.getResourceAsStream("/mapper/share/ShareLinkMapper.xml")) {
            new XMLMapperBuilder(xml, config, "share-mapper", config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /** 并发只能消耗一个查看名额，策略改变后旧版本无法再消耗名额。 */
    @Test
    void atomicallyConsumesViewsAndRejectsOldVersions() throws Exception {
        long id;
        try (var session = sessions.openSession(true); var statement = session.getConnection().createStatement()) {
            statement.execute("insert into share_link(code,source_id,file_path,file_name,max_views,cache_status) "
                    + "values('testcode',1,'/a.txt','a.txt',1,'2')");
            id = session.getMapper(ShareLinkMapper.class).selectShareLinkByCode("testcode").getLinkId();
        }
        var pool = Executors.newFixedThreadPool(8);
        try {
            var attempts = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 16; i++) attempts.add(() -> {
                try (var session = sessions.openSession(true)) {
                    return session.getMapper(ShareLinkMapper.class).incrementViewCount(id, 1L);
                }
            });
            int successful = 0;
            for (var result : pool.invokeAll(attempts)) successful += result.get();
            assertEquals(1, successful);
        } finally {
            pool.shutdownNow();
        }
        try (var session = sessions.openSession(true)) {
            ShareLinkMapper mapper = session.getMapper(ShareLinkMapper.class);
            ShareLink patch = new ShareLink();
            patch.setLinkId(id);
            patch.setMaxViews(10);
            patch.setPasswordHash("changed-password-hash");
            assertEquals(1, mapper.updateShareLink(patch));
            assertEquals(2L, mapper.selectShareLinkById(id).getAccessVersion());
            assertEquals(0, mapper.incrementViewCount(id, 1L));
            assertEquals(1, mapper.incrementViewCount(id, 2L));
            mapper.updateStatus(id, "1");
            assertEquals(0, mapper.incrementViewCount(id, 2L));
        }
    }
}

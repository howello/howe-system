package com.howe.meal.service;

import cn.hutool.core.util.IdUtil;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.mapper.MealOrderMapper;
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
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** 使用当前 Mapper XML 和模块 DDL，验证真实 MySQL 下的并发订单行为。 */
@EnabledIfEnvironmentVariable(named = "HOWE_TEST_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/howe_test.*")
class MealOrderSqlTest {
    private static SqlSessionFactory sessions;

    /** 仅初始化 CI 的 howe_test 测试库，不读取本地业务连接配置。 */
    @BeforeAll
    static void prepare() throws Exception {
        var data = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getenv("HOWE_TEST_MYSQL_URL"),
                "root", System.getenv("HOWE_TEST_MYSQL_PASSWORD"));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sql/meal.sql"))) root = root.getParent();
        assertNotNull(root);
        String sql = Files.readString(root.resolve("sql/meal.sql"));
        int begin = sql.indexOf("create table meal_order (");
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute("drop table if exists meal_order");
            statement.execute(sql.substring(begin, sql.indexOf(';', begin) + 1));
            statement.execute("alter table meal_order auto_increment = 1001");
        }
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), data));
        config.getTypeAliasRegistry().registerAlias("MealOrder", MealOrder.class);
        config.getTypeAliasRegistry().registerAlias("MealOrderItem", MealOrderItem.class);
        try (var xml = MealOrderSqlTest.class.getResourceAsStream("/mapper/meal/MealOrderMapper.xml")) {
            new XMLMapperBuilder(xml, config, "meal-order-mapper", config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /** 并发插入并定号均唯一，订单接单条件更新也只能由一个请求成功。 */
    @Test
    void allocatesUniqueNumbersAndAcceptsOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        long orderId;
        try {
            var creates = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 16; i++) creates.add(() -> {
                try (var session = sessions.openSession(false)) {
                    MealOrder order = new MealOrder();
                    order.setOrderNo(IdUtil.fastSimpleUUID());
                    order.setDeptId(10L);
                    order.setUserId(2L);
                    order.setUserName("测试用户");
                    order.setStatus("0");
                    order.setTotalCount(1);
                    order.setAssignMode("2");
                    var mapper = session.getMapper(MealOrderMapper.class);
                    mapper.insertMealOrder(order);
                    assertEquals(1, mapper.updateOrderNo(order.getOrderId(), "M20261102-" + order.getOrderId()));
                    session.commit();
                    return order.getOrderId();
                }
            });
            var ids = new HashSet<Long>();
            for (var result : pool.invokeAll(creates)) ids.add(result.get());
            assertEquals(16, ids.size());
            assertTrue(ids.stream().allMatch(id -> id > 999));
            orderId = ids.iterator().next();
            var accepts = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 16; i++) accepts.add(() -> {
                try (var session = sessions.openSession(true)) {
                    MealOrder update = new MealOrder();
                    update.setOrderId(orderId);
                    update.setStatus("1");
                    update.setExpectedStatus("0");
                    return session.getMapper(MealOrderMapper.class).updateMealOrderStatus(update);
                }
            });
            int successful = 0;
            for (var result : pool.invokeAll(accepts)) successful += result.get();
            assertEquals(1, successful);
        } finally {
            pool.shutdownNow();
        }
    }
}

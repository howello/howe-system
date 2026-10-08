-- 点餐订单通知：记录实际接单用户，供评价通知定位接收人。
-- 现有订单保持 NULL；仅新接单会写入接单用户ID。
ALTER TABLE meal_order
    ADD COLUMN accept_user_id BIGINT NULL COMMENT '接单厨师用户ID' AFTER accept_by;

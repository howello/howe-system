/**
 * 点餐分类默认图标集
 *
 * 与后端 {@code MealCategoryIconGuard} 的白名单保持一致：
 * 后端只接受这里的 emoji，不接受图片 URL 或任意文本。
 * 新增图标时必须同时更新后端白名单，并补一条契约测试。
 */
export interface MealCategoryIconGroup {
  /** 分组名称 */
  label: string;
  /** 分组内的 emoji */
  icons: string[];
}

/** 按用途分组的图标 */
export const MEAL_CATEGORY_ICON_GROUPS: MealCategoryIconGroup[] = [
  { label: "荤菜", icons: ["🍖", "🍗", "🥩", "🥓", "🍔", "🌭"] },
  { label: "半荤", icons: ["🥘"] },
  { label: "素菜", icons: ["🥬", "🥦", "🥒", "🥕", "🌽", "🍆", "🍄"] },
  { label: "水产", icons: ["🐟", "🐠", "🦐", "🦀", "🦑", "🦪"] },
  { label: "蛋豆", icons: ["🥚", "🍳", "🫘", "🧈"] },
  { label: "凉卤", icons: ["🥗", "🫒", "🧊", "🧄", "🌶️"] },
  { label: "汤羹", icons: ["🥣", "🍲", "🫕", "🍵", "🥄"] },
  { label: "面点", icons: ["🍜", "🍝", "🥟", "🥐", "🍞", "🥖", "🍚", "🍙"] },
  { label: "地方特色", icons: ["🏔️", "🫑", "🌿", "🥢", "🍯"] },
  { label: "其他", icons: ["🍽️", "✨", "⭐", "🔥", "❤️"] }
];

/** 默认图标：分类未选择图标时使用 */
export const DEFAULT_MEAL_CATEGORY_ICON = "🍽️";

/** 拍平后的全部图标（去重） */
export const MEAL_CATEGORY_ICONS: string[] = Array.from(
  new Set(MEAL_CATEGORY_ICON_GROUPS.flatMap((group) => group.icons))
);

/**
 * 判断一个值是否是允许的 emoji 图标
 *
 * @param icon 待判断的值
 * @returns 允许返回 true
 */
export function isAllowedMealCategoryIcon(icon?: string | null): boolean {
  if (!icon) {
    return false;
  }
  return MEAL_CATEGORY_ICONS.includes(icon.trim());
}

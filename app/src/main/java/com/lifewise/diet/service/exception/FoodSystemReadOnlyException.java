package com.lifewise.diet.service.exception;

/** 系统默认食物（user_id=NULL）禁止修改 / 删除（plan-04-diet §5.2 风险：系统食物被改/删）。 */
public class FoodSystemReadOnlyException extends RuntimeException {
    public FoodSystemReadOnlyException(Long foodId) {
        super("system food is read-only: id=" + foodId);
    }
}
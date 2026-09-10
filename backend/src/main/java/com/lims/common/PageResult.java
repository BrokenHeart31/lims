package com.lims.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应（api-spec 0.3）：records/total/current/size。
 * 由 MyBatis-Plus {@link IPage} 转换，屏蔽 MP 分页对象的其余字段。
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    /** Entity 分页 → VO 分页的映射转换 */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> mapper) {
        PageResult<V> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(mapper).toList());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }
}

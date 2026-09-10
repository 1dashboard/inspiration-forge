package io.github.onedashboard.inspirationforge.runtime.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class RuntimePageVO<T> implements Serializable {
    private List<T> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
}

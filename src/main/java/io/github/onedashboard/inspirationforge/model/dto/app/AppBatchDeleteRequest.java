package io.github.onedashboard.inspirationforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AppBatchDeleteRequest implements Serializable {
    private List<Long> ids;
}

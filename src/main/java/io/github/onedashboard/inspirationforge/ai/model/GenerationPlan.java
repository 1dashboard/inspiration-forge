package io.github.onedashboard.inspirationforge.ai.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GenerationPlan {
    private String title;
    private String summary;
    private List<String> pages = new ArrayList<>();
    private List<String> features = new ArrayList<>();
    private List<String> techStack = new ArrayList<>();
    private Boolean needsImages = false;
    private Boolean needsDatabase = false;
    private Boolean needsAuth = false;
    private List<String> roles = new ArrayList<>();
    private List<GenerationDataModel> dataModels = new ArrayList<>();
}

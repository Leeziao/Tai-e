/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static pascal.taie.util.collection.CollectionUtils.newHybridMap;

/**
 * Configuration for an analysis.
 */
class AnalysisConfig {

    @JsonProperty
    private String description;

    @JsonProperty
    private String analysisClass;

    @JsonProperty
    private String id;

    @JsonProperty
    private List<String> requires = emptyList();

    @JsonProperty
    private Map<String, Object> options = newHybridMap();

    String getDescription() {
        return description;
    }

    String getAnalysisClass() {
        return analysisClass;
    }

    String getId() {
        return id;
    }

    List<String> getRequires() {
        return requires;
    }

    Map<String, Object> getOptions() {
        return options;
    }

    public String toDetailedString() {
        return "AnalysisConfig{" +
                "description='" + description + '\'' +
                ", analysisClass='" + analysisClass + '\'' +
                ", id='" + id + '\'' +
                ", requires=" + requires +
                ", options=" + options +
                '}';
    }

    @Override
    public String toString() {
        return id;
    }
}

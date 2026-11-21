package com.examsystem.common.dto;

import java.util.List;

/** 统一分页响应；page 从 1 开始，total 必须按与 items 相同的筛选及授权条件计算。 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {}

package com.fitmind.common;
import lombok.AllArgsConstructor; import lombok.Data; import java.util.List;
@Data @AllArgsConstructor public class PageResult<T> { private long page; private long size; private long total; private List<T> records; }

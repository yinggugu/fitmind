package com.fitmind.common;
import lombok.AllArgsConstructor; import lombok.Data;
@Data @AllArgsConstructor
public class Result<T> { private int code; private String message; private T data; public static <T> Result<T> ok(T data){return new Result<>(200,"success",data);} public static Result<Void> ok(){return ok(null);} public static Result<Void> error(int code,String message){return new Result<>(code,message,null);} }

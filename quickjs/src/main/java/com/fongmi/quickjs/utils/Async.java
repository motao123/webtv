package com.fongmi.quickjs.utils;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java.util.concurrent.CompletableFuture;

public class Async {

    private CompletableFuture<Object> future;

    private final JSCallFunction success = args -> {
        future.complete(args != null && args.length > 0 ? args[0] : null);
        return null;
    };

    private final JSCallFunction error = args -> {
        String msg = args != null && args.length > 0 && args[0] != null ? args[0].toString() : "";
        future.completeExceptionally(new Exception(msg));
        return null;
    };

    private Async() {
        this.future = new CompletableFuture<>();
    }

    public static CompletableFuture<Object> run(JSObject object, String name, Object... args) {
        return new Async().call(object, name, args);
    }

    private CompletableFuture<Object> call(JSObject object, String name, Object... args) {
        JSFunction func = object.getJSFunction(name);
        if (func == null) return empty();
        call(func, args);
        return future;
    }

    private CompletableFuture<Object> empty() {
        future.complete(null);
        return future;
    }

    private void call(JSFunction func, Object... args) {
        try {
            Object result = func.call(args);
            if (result instanceof JSObject) then((JSObject) result);
            else future.complete(result);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        } finally {
            func.release();
        }
    }

    private void then(JSObject promise) {
        JSFunction then = promise.getJSFunction("then");
        if (then == null) {
            future.complete(promise);
        } else {
            consume(then, success);
            consume(promise.getJSFunction("catch"), error);
        }
    }

    private void consume(JSFunction func, JSCallFunction callback) {
        if (func == null) return;
        try {
            func.call(callback);
        } finally {
            func.release();
        }
    }

}

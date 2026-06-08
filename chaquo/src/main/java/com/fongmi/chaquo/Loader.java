package com.fongmi.chaquo;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.utils.Path;

public class Loader {

    private final PyObject app;

    public Loader() {
        if (!Python.isStarted()) Python.start(Platform.create());
        app = Python.getInstance().getModule("app");
    }

    public Spider spider(String api) {
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), api);
        return new Spider(app, obj, api);
    }
}

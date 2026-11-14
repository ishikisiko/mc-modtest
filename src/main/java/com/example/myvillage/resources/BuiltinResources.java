package com.example.myvillage.resources;

import java.io.*;

/** 生成资源的统一内置读取入口；资源包覆盖仍由调用方的 ResourceManager 负责。 */
public final class BuiltinResources {
    public static InputStream open(String path) throws IOException {
        String key=path.startsWith("/")?path.substring(1):path;
        if(!PackedResourceStore.validPath(key))throw new IOException("非法内置资源路径："+path);
        InputStream direct=BuiltinResources.class.getResourceAsStream("/"+key);
        if(direct!=null)return direct;
        return Holder.STORE.open(key);
    }
    private static final class Holder {
        static final PackedResourceStore STORE=load();
        private static PackedResourceStore load(){
            try{return PackedResourceStore.classpath();}catch(IOException error){throw new UncheckedIOException("无法读取当前共享资源",error);}
        }
    }
    private BuiltinResources(){}
}

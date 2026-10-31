package com.example.myvillage.manor;

import java.net.URLClassLoader;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import static org.junit.jupiter.api.Assertions.*;

class ManorOptionalLoadingTest {
    private URLClassLoader isolated(boolean jadePresent) {
        var classes=ManorPresentation.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new java.net.URL[]{classes},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("net.minecraft.client.")||name.startsWith("com.example.myvillage.client."))throw new ClassNotFoundException("client unavailable: "+name);
                if(!jadePresent&&name.startsWith("snownee.jade."))throw new ClassNotFoundException("Jade not installed: "+name);
                if(name.startsWith("com.example.myvillage.manor.")||name.startsWith("com.example.myvillage.compat.jade.")) {
                    synchronized(getClassLoadingLock(name)) {
                        Class<?> found=findLoadedClass(name);if(found==null)found=findClass(name);if(resolve)resolveClass(found);return found;
                    }
                }
                return super.loadClass(name,resolve);
            }
        };
    }
    @Test void commonBlocksItemsAndDescriptionsLoadWithoutJadeOrClientClasses() throws Exception {
        try(var loader=isolated(false)) {
            for(String type:List.of("ManorPresentation","ManorComponentBlock","ManorComponentItem","ManorLocation"))
                assertTrue(loader.loadClass("com.example.myvillage.manor."+type).getDeclaredMethods().length>0);
            var type=loader.loadClass("com.example.myvillage.manor.ManorPresentation");
            Object catalog=type.getMethod("get").invoke(null);
            Object info=type.getMethod("component",String.class,int.class).invoke(catalog,"manor_lingyue_carved_7",21);
            assertEquals("manor_part.myvillage.window",info.getClass().getMethod("nameKey").invoke(info));
        }
    }
    @Test void jadeCommonRegistrationLoadsOnADedicatedServerWithoutClientClasses() throws Exception {
        try(var loader=isolated(true)) {
            var type=Class.forName("com.example.myvillage.compat.jade.ManorJadePlugin",true,loader);
            var plugin=(IWailaPlugin)type.getConstructor().newInstance();
            int[] registered={0};
            var registration=(IWailaCommonRegistration)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IWailaCommonRegistration.class},(p,m,a)->{
                assertEquals("registerBlockDataProvider",m.getName());
                assertEquals("com.example.myvillage.compat.jade.ManorJadeDataProvider",a[0].getClass().getName());registered[0]++;return null;
            });
            plugin.register(registration);assertEquals(1,registered[0]);
        }
    }
}

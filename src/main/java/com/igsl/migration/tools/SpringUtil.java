package com.igsl.migration.tools;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SpringUtil implements ApplicationContextAware {
    //Spring应用上下文环境
    private static ApplicationContext applicationContext = null;

    /**
     * 实现ApplicationContextAware 接口的回调方法。设置上下文环境
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取对象
     * @return  Object 一个以所给名字注册的bean的实例 (service注解方式，自动生成以首字母小写的类名为bean name)
     */
    public static Object getBean(String name) throws BeansException {
        return applicationContext.getBean(name);
    }

    /**
     * 通过class获取对象
     * @param clazz
     * @param <T>
     * @return
     * @throws BeansException
     */
    public static <T> T getBean(Class<T> clazz) throws BeansException {
        return applicationContext.getBean(clazz);
    }
    
    /**
	 * 手动分页方法
	 * @param dataList
	 * @param pageSize
	 * @param currentPage
	 * @return
	 */
    public static List manualPage(List dataList, long pageSize, long currentPage) {
        List<Object> currentPageList = new ArrayList<>();
        if (dataList != null && dataList.size() > 0) {
            long currIdx = (currentPage > 1 ? (currentPage - 1) * pageSize : 0);
            for (int i = 0; i < pageSize && i < dataList.size() - currIdx; i++) {
                Object o = dataList.get((int) (currIdx + i));
                currentPageList.add(o);
            }
        }
        return currentPageList;
    }

}

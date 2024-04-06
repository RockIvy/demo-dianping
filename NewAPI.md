## 该文件主要记录在项目学习过程中遇到的有用的工具类等

### huto

```java
 /**
 * description:生成随机字符串
 * isSimple：是否有 - 连接
 * 场景：生成token
*/
UUID.randomUUID().toString(isSimple);

/**
 * 使用Map填充Bean对象
 *
 * @param <T>           Bean类型
 * @param map           Map
 * @param bean          Bean
 * @param isIgnoreError 是否忽略注入错误
 * @return Bean
*/
static <T> T fillBeanWithMap(Map<?, ?> map, T bean, boolean isIgnoreError)

 
/**
 * 对象转Map，不进行驼峰转下划线，不忽略值为空的字段
 *
 * @param bean bean对象
 * @return Map
*/
static Map<String, Object> beanToMap(Object bean)    

/**
* 对象转Map
* 通过自定义CopyOptions完成抓换选项，以便实现：
*
* <pre>
* 1. 字段筛选，可以去除不需要的字段
* 2. 字段变换，例如实现驼峰转下划线
* 3. 自定义字段前缀或后缀等等
* 4. 字段值处理
* ...
* </pre>
*
* @param bean        bean对象
* @param targetMap   目标的Map
* @param copyOptions 拷贝选项
* @return Map
* @since 5.7.15
*/    
static Map<String, Object> beanToMap(Object bean, Map<String, Object> targetMap, CopyOptions copyOptions)
    
/**
 * 按照Bean对象属性创建对应的Class对象，并忽略某些属性
 *
 * @param <T>              对象类型
 * @param source           源Bean对象
 * @param tClass           目标Class
 * @param ignoreProperties 不拷贝的的属性列表
 * @return 目标对象
*/
static <T> T copyProperties(Object source, Class<T> tClass, String... ignoreProperties)  
```


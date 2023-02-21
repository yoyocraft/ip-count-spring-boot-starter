# 自定义Starter开发

## 记录系统访客独立IP访问次数

1. 每次访问网站均进行统计
2. 后台每10秒输出一次监控信息（格式：IP + 访问次数）

### 需求分析

1. 数据记录位置：Map / Redis
2. 功能触发位置：每次Web请求（拦截器，网关）
3. 业务参数（配置项）
   1. 输出频度：默认10秒，用户可自定义
   2. 数据特征：累计数据 / 阶段数据，默认是累计数据
   3. 输出格式：详细模式 / 极简模式
4. 校验环境，设置加载条件



### todo

1. 输出到控制台改为输出为文件，以日志的形式输出
2. 加分布式锁 保证线程安全 -- Redission实现
3. 打包上传

### YL-2-2.IP计数业务功能开发（自定义starter）

​		本功能最终要实现的效果是在现有的项目中导入一个starter，对应的功能就添加上了，删除掉对应的starter，功能就消失了，要求功能要与原始项目完全解耦。因此需要开发一个独立的模块，制作对应功能。

**步骤一：创建全新的模块，定义业务功能类**

​		功能类的制作并不复杂，定义一个业务类，声明一个Map对象，用于记录ip访问次数，key是ip地址，value是访问次数

```JAVA
public class IpCountService {
    private Map<String,Integer> ipCountMap = new HashMap<String,Integer>();
}
```

​		有些小伙伴可能会有疑问，不设置成静态的，如何在每次请求时进行数据共享呢？记得，当前类加载成bean以后是一个单例对象，对象都是单例的，哪里存在多个对象共享变量的问题。

**步骤二：制作统计功能**

​		制作统计操作对应的方法，每次访问后对应ip的记录次数+1。需要分情况处理，如果当前没有对应ip的数据，新增一条数据，否则就修改对应key的值+1即可

```JAVA
public class IpCountService {
    private Map<String,Integer> ipCountMap = new HashMap<String,Integer>();
    public void count(){
        //每次调用当前操作，就记录当前访问的IP，然后累加访问次数
        //1.获取当前操作的IP地址
        String ip = null;
        //2.根据IP地址从Map取值，并递增
        Integer count = ipCountMap.get(ip);
        if(count == null){
            ipCountMap.put(ip,1);
        }else{
            ipCountMap.put(ip,count + 1);
        }
    }
}
```

​		因为当前功能最终导入到其他项目中进行，而导入当前功能的项目是一个web项目，可以从容器中直接获取请求对象，因此获取IP地址的操作可以通过自动装配得到请求对象，然后获取对应的访问IP地址。

```JAVA
public class IpCountService {
    private Map<String,Integer> ipCountMap = new HashMap<String,Integer>();
    @Autowired
    //当前的request对象的注入工作由使用当前starter的工程提供自动装配
    private HttpServletRequest httpServletRequest;
    public void count(){
        //每次调用当前操作，就记录当前访问的IP，然后累加访问次数
        //1.获取当前操作的IP地址
        String ip = httpServletRequest.getRemoteAddr();
        //2.根据IP地址从Map取值，并递增
        Integer count = ipCountMap.get(ip);
        if(count == null){
            ipCountMap.put(ip,1);
        }else{
            ipCountMap.put(ip,count + 1);
        }
    }
}
```

**步骤三：定义自动配置类**

​		我们需要做到的效果是导入当前模块即开启此功能，因此使用自动配置实现功能的自动装载，需要开发自动配置类在启动项目时加载当前功能。

```JAVA
public class IpAutoConfiguration {
    @Bean
    public IpCountService ipCountService(){
        return new IpCountService();
    }
}
```

​		自动配置类需要在spring.factories文件中做配置方可自动运行。

```properties
# Auto Configure
org.springframework.boot.autoconfigure.EnableAutoConfiguration=cn.itcast.autoconfig.IpAutoConfiguration
```

**步骤四：在原始项目中模拟调用，测试功能**

​		原始调用项目中导入当前开发的starter

```XML
<dependency>
    <groupId>cn.itcast</groupId>
    <artifactId>ip_spring_boot_starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

​		推荐选择调用方便的功能做测试，推荐使用分页操作，当然也可以换其他功能位置进行测试。

```JAVA
@RestController
@RequestMapping("/books")
public class BookController {
    @Autowired
    private IpCountService ipCountService;
    @GetMapping("{currentPage}/{pageSize}")
    public R getPage(@PathVariable int currentPage,@PathVariable int pageSize,Book book){
        ipCountService.count();
        IPage<Book> page = bookService.getPage(currentPage, pageSize,book);
        if( currentPage > page.getPages()){
            page = bookService.getPage((int)page.getPages(), pageSize,book);
        }
        return new R(true, page);
    }
}
```

<font color="#f0f"><b>温馨提示</b></font>

​		由于当前制作的功能需要在对应的调用位置进行坐标导入，因此必须保障仓库中具有当前开发的功能，所以每次原始代码修改后，需要重新编译并安装到仓库中。为防止问题出现，建议每次安装之前先clean然后install，保障资源进行了更新。切记切记！！

**当前效果**

​		每次调用分页操作后，可以在控制台输出当前访问的IP地址，此功能可以在count操作中添加日志或者输出语句进行测试。



### YL-2-3.定时任务报表开发

​		当前已经实现了在业务功能类中记录访问数据，但是还没有输出监控的信息到控制台。由于监控信息需要每10秒输出1次，因此需要使用定时器功能。可以选取第三方技术Quartz实现，也可以选择Spring内置的task来完成此功能，此处选用Spring的task作为实现方案。

**步骤一：开启定时任务功能**

​		定时任务功能开启需要在当前功能的总配置中设置，结合现有业务设定，比较合理的位置是设置在自动配置类上。加载自动配置类即启用定时任务功能。

```JAVA
@EnableScheduling
public class IpAutoConfiguration {
    @Bean
    public IpCountService ipCountService(){
        return new IpCountService();
    }
}
```

**步骤二：制作显示统计数据功能**

​		定义显示统计功能的操作print()，并设置定时任务，当前设置每5秒运行一次统计数据。

```JAVA
public class IpCountService {
    private Map<String,Integer> ipCountMap = new HashMap<String,Integer>();
    @Scheduled(cron = "0/5 * * * * ?")
    public void print(){
        System.out.println("         IP访问监控");
        System.out.println("+-----ip-address-----+--num--+");
        for (Map.Entry<String, Integer> entry : ipCountMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println(String.format("|%18s  |%5d  |",key,value));
        }
        System.out.println("+--------------------+-------+");
      }
}
```

​		其中关于统计报表的显示信息拼接可以使用各种形式进行，此处使用String类中的格式化字符串操作进行，学习者可以根据自己的喜好调整实现方案。

<font color="#f0f"><b>温馨提示</b></font>

​		每次运行效果之前先clean然后install，切记切记！！

**当前效果**

​		每次调用分页操作后，可以在控制台看到统计数据，到此基础功能已经开发完毕。



### YL-2-4.使用属性配置设置功能参数

​		由于当前报表显示的信息格式固定，为提高报表信息显示的灵活性，需要通过yml文件设置参数，控制报表的显示格式。

**步骤一：定义参数格式**

​		设置3个属性，分别用来控制显示周期（cycle），阶段数据是否清空（cycleReset），数据显示格式（model）

```YAML
tools:
  ip:
    cycle: 10
    cycleReset: false
    model: "detail"
```

**步骤二：定义封装参数的属性类，读取配置参数**

​		为防止项目组定义的参数种类过多，产生冲突，通常设置属性前缀会至少使用两级属性作为前缀进行区分。

​		日志输出模式是在若干个类别选项中选择某一项，对于此种分类性数据建议制作枚举定义分类数据，当然使用字符串也可以。

```JAVA
@ConfigurationProperties(prefix = "tools.ip")
public class IpProperties {
    /**
     * 日志显示周期
     */
    private Long cycle = 5L;
    /**
     * 是否周期内重置数据
     */
    private Boolean cycleReset = false;
    /**
     * 日志输出模式  detail：详细模式  simple：极简模式
     */
    private String model = LogModel.DETAIL.value;
    public enum LogModel{
        DETAIL("detail"),
        SIMPLE("simple");
        private String value;
        LogModel(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
    }
}
```

**步骤三：加载属性类**

```JAVA
@EnableScheduling
@EnableConfigurationProperties(IpProperties.class)
public class IpAutoConfiguration {
    @Bean
    public IpCountService ipCountService(){
        return new IpCountService();
    }
}
```

**步骤四：应用配置属性**

​		在应用配置属性的功能类中，使用自动装配加载对应的配置bean，然后使用配置信息做分支处理。

​		注意：清除数据的功能一定要在输出后运行，否则每次查阅的数据均为空白数据。

```JAVA
public class IpCountService {
    private Map<String,Integer> ipCountMap = new HashMap<String,Integer>();
    @Autowired
    private IpProperties ipProperties;
    @Scheduled(cron = "0/5 * * * * ?")
    public void print(){
        if(ipProperties.getModel().equals(IpProperties.LogModel.DETAIL.getValue())){
            System.out.println("         IP访问监控");
            System.out.println("+-----ip-address-----+--num--+");
            for (Map.Entry<String, Integer> entry : ipCountMap.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                System.out.println(String.format("|%18s  |%5d  |",key,value));
            }
            System.out.println("+--------------------+-------+");
        }else if(ipProperties.getModel().equals(IpProperties.LogModel.SIMPLE.getValue())){
            System.out.println("     IP访问监控");
            System.out.println("+-----ip-address-----+");
            for (String key: ipCountMap.keySet()) {
                System.out.println(String.format("|%18s  |",key));
            }
            System.out.println("+--------------------+");
        }
        //阶段内统计数据归零
        if(ipProperties.getCycleReset()){
            ipCountMap.clear();
        }
    }
}
```

<font color="#f0f"><b>温馨提示</b></font>

​		每次运行效果之前先clean然后install，切记切记！！

**当前效果**

​		在web程序端可以通过控制yml文件中的配置参数对统计信息进行格式控制。但是数据显示周期还未进行控制。



### YL-2-5.使用属性配置设置定时器参数

​		在使用属性配置中的显示周期数据时，遇到了一些问题。由于无法在@Scheduled注解上直接使用配置数据，改用曲线救国的方针，放弃使用@EnableConfigurationProperties注解对应的功能，改成最原始的bean定义格式。

**步骤一：@Scheduled注解使用#{}读取bean属性值**

​		此处读取bean名称为ipProperties的bean的cycle属性值

```JAVA
@Scheduled(cron = "0/#{ipProperties.cycle} * * * * ?")
public void print(){
}
```

**步骤二：属性类定义bean并指定bean的访问名称**

​		如果此处不设置bean的访问名称，spring会使用自己的命名生成器生成bean的长名称，无法实现属性的读取

```JAVA
@Component("ipProperties")
@ConfigurationProperties(prefix = "tools.ip")
public class IpProperties {
}
```

**步骤三：弃用@EnableConfigurationProperties注解对应的功能，改为导入bean的形式加载配置属性类**

```JAVA
@EnableScheduling
//@EnableConfigurationProperties(IpProperties.class)
@Import(IpProperties.class)
public class IpAutoConfiguration {
    @Bean
    public IpCountService ipCountService(){
        return new IpCountService();
    }
}
```

<font color="#f0f"><b>温馨提示</b></font>

​		每次运行效果之前先clean然后install，切记切记！！

**当前效果**

​		在web程序端可以通过控制yml文件中的配置参数对统计信息的显示周期进行控制



### YL-2-6.拦截器开发

​		基础功能基本上已经完成了制作，下面进行拦截器的开发。开发时先在web工程中制作，然后将所有功能挪入starter模块中

**步骤一：开发拦截器**

​		使用自动装配加载统计功能的业务类，并在拦截器中调用对应功能

```JAVA
public class IpCountInterceptor implements HandlerInterceptor {
    @Autowired
    private IpCountService ipCountService;
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, Object handler) throws Exception {
        ipCountService.count();
        return true;
    }
}
```

**步骤二：配置拦截器**

​		配置mvc拦截器，设置拦截对应的请求路径。此处拦截所有请求，用户可以根据使用需要设置要拦截的请求。甚至可以在此处加载IpCountProperties中的属性，通过配置设置拦截器拦截的请求。

```JAVA
@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipCountInterceptor()).addPathPatterns("/**");
    }
    @Bean
    public IpCountInterceptor ipCountInterceptor(){
        return new IpCountInterceptor();
    }
}
```

<font color="#f0f"><b>温馨提示</b></font>

​		每次运行效果之前先clean然后install，切记切记！！

**当前效果**

​		在web程序端导入对应的starter后功能开启，去掉坐标后功能消失，实现自定义starter的效果。

​		到此当前案例全部完成，自定义stater的开发其实在第一轮开发中就已经完成了，就是创建独立模块导出独立功能，需要使用的位置导入对应的starter即可。如果是在企业中开发，记得不仅需要将开发完成的starter模块install到自己的本地仓库中，开发完毕后还要deploy到私服上，否则别人就无法使用了。



### YL-2-7.功能性完善——开启yml提示功能

​		我们在使用springboot的配置属性时，都可以看到提示，尤其是导入了对应的starter后，也会有对应的提示信息出现。但是现在我们的starter没有对应的提示功能，这种设定就非常的不友好，本节解决自定义starter功能如何开启配置提示的问题。

​		springboot提供有专用的工具实现此功能，仅需要导入下列坐标。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

​		程序编译后，在META-INF目录中会生成对应的提示文件，然后拷贝生成出的文件到自己开发的META-INF目录中，并对其进行编辑。打开生成的文件，可以看到如下信息。其中groups属性定义了当前配置的提示信息总体描述，当前配置属于哪一个属性封装类，properties属性描述了当前配置中每一个属性的具体设置，包含名称、类型、描述、默认值等信息。hints属性默认是空白的，没有进行设置。hints属性可以参考springboot源码中的制作，设置当前属性封装类专用的提示信息，下例中为日志输出模式属性model设置了两种可选提示信息。

```JAVA
{
  "groups": [
    {
      "name": "tools.ip",
      "type": "cn.itcast.properties.IpProperties",
      "sourceType": "cn.itcast.properties.IpProperties"
    }
  ],
  "properties": [
    {
      "name": "tools.ip.cycle",
      "type": "java.lang.Long",
      "description": "日志显示周期",
      "sourceType": "cn.itcast.properties.IpProperties",
      "defaultValue": 5
    },
    {
      "name": "tools.ip.cycle-reset",
      "type": "java.lang.Boolean",
      "description": "是否周期内重置数据",
      "sourceType": "cn.itcast.properties.IpProperties",
      "defaultValue": false
    },
    {
      "name": "tools.ip.model",
      "type": "java.lang.String",
      "description": "日志输出模式  detail：详细模式  simple：极简模式",
      "sourceType": "cn.itcast.properties.IpProperties"
    }
  ],
  "hints": [
    {
      "name": "tools.ip.model",
      "values": [
        {
          "value": "detail",
          "description": "详细模式."
        },
        {
          "value": "simple",
          "description": "极简模式."
        }
      ]
    }
  ]
}
```

**总结**

1. 自定义starter其实就是做一个独立的功能模块，核心技术是利用自动配置的效果在加载模块后加载对应的功能
2. 通常会为自定义starter的自动配置功能添加足够的条件控制，而不会做成100%加载对功能的效果
3. 本例中使用map保存数据，如果换用redis方案，在starter开发模块中就要导入redis对应的starter
4. 对于配置属性务必开启提示功能，否则使用者无法感知配置应该如何书写
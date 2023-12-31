//package com.hmdp;
//
//import com.hmdp.entity.Shop;
//import com.hmdp.service.IShopService;
//import com.hmdp.utils.RedisIdWorker;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.geo.Point;
//import org.springframework.data.redis.core.StringRedisTemplate;
//
//import javax.annotation.Resource;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.Executor;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.stream.Collectors;
//
//@SpringBootTest
//class HmDianPingApplicationTests {
//
//    @Resource
//    RedisIdWorker redisIdWorker;
//
//    @Resource
//    IShopService shopService;
//    @Resource
//    StringRedisTemplate stringRedisTemplate;
//
//    private ExecutorService es = Executors.newFixedThreadPool(500);
//    @Test
//     void testIdWorker() throws InterruptedException {
//        CountDownLatch latch  = new CountDownLatch(300);
//        Runnable task = () -> {
//            for (int i = 0; i < 100; i++) {
//                long id = redisIdWorker.nextId("order");
//                System.out.println(id);
//            }
//            latch.countDown();
//        };
//        long begin = System.currentTimeMillis();
//        for (int i = 0; i < 300; i++) {
//            es.submit(task);
//        }
//        latch.await();
//        long end = System.currentTimeMillis();
//
//        System.out.println("time = "  + (end -begin));
//
//    }
//
//    @Test
//    void loadShopData(){
//        //1.查询店铺信息
//        List<Shop> list = shopService.list();
//        //2.店铺分组，按照typeId分组
//        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//        //3.分批存储写入redis
//        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
//            //获取类型id
//            Long typeId = entry.getKey();
//            String key = "shop:geo:" + typeId;
//            //获取同类型店铺的集合
//            List<Shop> value = entry.getValue();
//
//            //写入redis GEOADD key 精度 纬度 member
//            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(),shop.getY()),shop.getId().toString());
//            }
//        }
//
//    }
//
//
//}

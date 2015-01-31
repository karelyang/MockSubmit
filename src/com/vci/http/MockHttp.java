package com.vci.http;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MockHttp {

    //存放提交的结果数据
    public static final Map<Integer, String> cache_result = Collections.synchronizedMap(new HashMap<>());
    private static final String CODE_URL = "http://211.143.120.130/yy/CheckCode.aspx";
    private static final String URL = "http://211.143.120.130/yy/submit.aspx?GNDH=";
    private static final String FORM_STR = "CNM999";
    private static final String COACH_STR = "CNM997";
    private static final String CHECK_STR = "CNM998";
    private static final List<String> user_agents = new ArrayList<>();
    private static MockHttp instance = null;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private CloseableHttpClient client = null;
    private HttpClientContext context = null;
    private  ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8);

    private MockHttp() {
        getThreadSafeClient();
        getHttpContext();
        user_agents.add("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.99 Safari/537.36");
        user_agents.add("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.93 Safari/537.36");
        user_agents.add("Mozilla/5.0 (Windows NT 6.3; Trident/7.0; rv:11.0) like Gecko");//IE11
        user_agents.add("Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; Touch; rv:11.0) like Gecko");//64位IE11
        user_agents.add("Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)");//IE10
        user_agents.add("Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 7.1; Trident/5.0)");//IE9
        user_agents.add("Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0)");//IE8
        user_agents.add("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0");//FF31
    }

    public static MockHttp getInstance() {
        if (instance == null) {
            instance = new MockHttp();
        }
        return instance;
    }

    public void closeClient() throws IOException {
        if (client != null)
            client.close();
    }

    /**
     * 获取验证码
     */
    public void generateCode() throws IOException {
        HttpGet get = new HttpGet(CODE_URL);
        CloseableHttpResponse response = client.execute(get, context);

        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            try (FileOutputStream fos = new FileOutputStream(Main.CODE_NAME);
                 InputStream is = response.getEntity().getContent()) {
                IOUtils.copy(is, fos);
            }
        }
    }

    /**
     * @param schoolName 驾校名称
     * @return [0]车号，[1]教练
     */
    private String[] getCarNoAndCoach(String schoolName, String userAgent) throws IOException {
        String[] result = new String[2];
        HttpPost post = new HttpPost(URL + COACH_STR);
        post.setHeader(HTTP.USER_AGENT, userAgent);

        List<NameValuePair> urlParameters = new ArrayList<>();

        urlParameters.add(new BasicNameValuePair("JXMC", schoolName));
        urlParameters.add(new BasicNameValuePair("KSCX", "C1:小型汽车"));
        urlParameters.add(new BasicNameValuePair("KSKM", "3:科目三"));

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(urlParameters, "UTF-8");
        post.setEntity(entity);

        try (CloseableHttpResponse response = client.execute(post, context);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            IOUtils.copy(response.getEntity().getContent(), baos);

            String str = baos.toString("UTF-8");
            result[0] = str.substring(str.indexOf("Option(") + 8, str.indexOf(',') - 1);
            result[1] = str.substring(str.lastIndexOf(',') + 3, str.lastIndexOf("'"));
        }
        return result;
    }

    /**
     * 提交数据
     */
    public void post(String code, Map<Integer, String[]> persons) throws IOException {
        HttpPost post = new HttpPost(URL + FORM_STR);

        for (Integer lineNum : persons.keySet()) {
            if (cache_result.containsKey(lineNum))
                continue;

            String[] person = persons.get(lineNum);

            String userAgent = getRandomUserAgent();
            post.setHeader(HTTP.USER_AGENT, userAgent);
            post.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
            post.setHeader(HTTP.TARGET_HOST, "211.143.120.130");
            post.setHeader("Referer", "http://211.143.120.130/yy/ksyy.aspx");
            post.setHeader("Accept-Encoding", "gzip, deflate");
            post.setHeader("Accept", "text/html,application/xhtml+xml,*/*;");

            String[] carNoAndCoach = getCarNoAndCoach(person[1], userAgent);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .setBoundary(getBoundary())
                    .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addPart("SFZMMC", getUTF8Body("A:居民身份证"))
                    .addTextBody("SFZMHM", person[2])
                    .addPart("XM", getUTF8Body(person[0]))
                    .addPart("KSCX", getUTF8Body("C1:小型汽车"))
                    .addTextBody("LXDH", person[3])
                    .addPart("JXMC", getUTF8Body(person[1]))
                    .addTextBody("KSRQ", person[5])
                    .addPart("KSKM", getUTF8Body("3:科目三"))
                    .addPart("KSDD", getUTF8Body(person[4]))
                    .addPart("JLCH", getUTF8Body(carNoAndCoach[0]))
                    .addPart("JLY", getUTF8Body(carNoAndCoach[1]))
                    .addTextBody("YZM", code);

            post.setEntity(builder.build());

            try (CloseableHttpResponse response = client.execute(post, context);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                IOUtils.copy(response.getEntity().getContent(), baos);

                String returnStr = baos.toString("UTF-8");

                if (returnStr.contains("\"")) {
                    String alertStr = parseResult(returnStr);

                    String timeStr = LocalTime.now().format(formatter);
                    System.err.println("<" + person[0] + ">" + timeStr + " " + alertStr);

                    if (alertStr.contains("验证码")) {
                        cache_result.putIfAbsent(0, alertStr);
                        return;
                    } else if (!alertStr.contains("已满"))
                        cache_result.put(lineNum, alertStr);

                    Thread.sleep(1000 * 10);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 查询结果
     */
    public Map<Integer, String> check(Map<Integer, String[]> persons) throws IOException {
        Map<Integer, String> result = Collections.synchronizedMap(new HashMap<>(persons.size()));

        HttpPost post = new HttpPost(URL + CHECK_STR);
        post.setHeader(HTTP.USER_AGENT, getRandomUserAgent());

        persons.keySet().parallelStream().forEach(lineNum -> {
            String[] person = persons.get(lineNum);

            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair("XM", person[0]));
            urlParameters.add(new BasicNameValuePair("SFZMHM", person[2]));
            urlParameters.add(new BasicNameValuePair("KSCX", "C1:小型汽车"));
            urlParameters.add(new BasicNameValuePair("LXDH", person[3]));
            urlParameters.add(new BasicNameValuePair("KSRQ", person[5]));
            urlParameters.add(new BasicNameValuePair("KSKM", "3:科目三"));

            try {
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(urlParameters, "UTF-8");
                post.setEntity(entity);
            } catch (UnsupportedEncodingException e) {
            }

            try (CloseableHttpResponse response = client.execute(post, context);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                IOUtils.copy(response.getEntity().getContent(), baos);
                String returnStr = baos.toString("UTF-8");

                System.out.println(returnStr);

                if (returnStr.contains("\"")) {
                    result.put(lineNum, parseResult(returnStr));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return result;
    }

    /**
     * 获取线程安全的Client
     */
    private CloseableHttpClient getThreadSafeClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY)
                .setConnectTimeout(30 * 1000 * 10)
                .setSocketTimeout(30 * 1000 * 10)
                .build();

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setDefaultMaxPerRoute(32);
        connManager.setMaxTotal(128);

        client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connManager)
                .build();

        return client;
    }

    /**
     * 获取HttpContext
     */
    private HttpClientContext getHttpContext() {
        CookieStore cookieStore = new BasicCookieStore();

        context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        return context;
    }

    //截取返回的字符串
    private String parseResult(String str) {
        return str.substring(str.indexOf("\"") + 1, str.lastIndexOf("\""));
    }

    //随机获取代理字符串
    private String getRandomUserAgent() {
        return user_agents.get((int) (Math.random() * user_agents.size()));
    }

    //转换中文为UTF-8表单数据
    private StringBody getUTF8Body(String value){
        return new StringBody(value, contentType);
    }

    //获取长度为20的随机字符（\w），用于表单数据提交
    private String getBoundary() {
        StringBuilder sb = new StringBuilder("----");
        Random rand = new Random();//随机用以下三个随机生成器
        Random randData = new Random();
        int data;
        for (int i = 0; i < 16; i++) {
            int index = rand.nextInt(3);
            //目的是随机选择生成数字，大小写字母
            switch (index) {
                case 0:
                    data = randData.nextInt(10);//仅仅会生成0~9
                    sb.append(data);
                    break;
                case 1:
                    data = randData.nextInt(26) + 65;//保证只会产生65~90之间的整数
                    sb.append((char) data);
                    break;
                case 2:
                    data = randData.nextInt(26) + 97;//保证只会产生97~122之间的整数
                    sb.append((char) data);
                    break;
            }
        }
        return sb.toString();
    }
}
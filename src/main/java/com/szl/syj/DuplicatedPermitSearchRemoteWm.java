package com.szl.syj;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Multimap;
import com.szl.syj.integrated.RuleOCR6__;
import com.szl.syj.integrated.SinglePicProcess;
import com.szl.syj.mongo.MongoOperator;
import com.szl.syj.utils.Triple;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Created by Administrator on 2018/5/15.
 */
@Component
public class DuplicatedPermitSearchRemoteWm {
    static private Connection conn = null;
    @Autowired
    private MongoOperator operator;
    @Autowired
    private SinglePicProcess singlePicProcess;

    public Multimap<String, String> getTgResMap() {
        return tgResMap;
    }

    public void setTgResMap(Multimap<String, String> tgResMap) {
        this.tgResMap = tgResMap;
    }



    public Multimap<String, String> getWmResMap() {
        return wmResMap;
    }

    public void setWmResMap(Multimap<String, String> wmResMap) {
        this.wmResMap = wmResMap;
    }

    public BiMap<String, String> getMD5toIndex() {
        return MD5toIndex;
    }

    public void setMD5toIndex(BiMap<String, String> MD5toIndex) {
        this.MD5toIndex = MD5toIndex;
    }

    public Multimap<String, String> getSid2Index() {
        return sid2Index;
    }

    public void setSid2Index(Multimap<String, String> sid2Index) {
        this.sid2Index = sid2Index;
    }

    public Multimap<String, String> getSid2RawId() {
        return sid2RawId;
    }

    public void setSid2RawId(Multimap<String, String> sid2RawId) {
        this.sid2RawId = sid2RawId;
    }

    public HashMap<String, String> getIndex2Sid() {
        return index2Sid;
    }

    public void setIndex2Sid(HashMap<String, String> index2Sid) {
        this.index2Sid = index2Sid;
    }

    public HashMap<String, String> getRawId2Sid() {
        return rawId2Sid;
    }

    public void setRawId2Sid(HashMap<String, String> rawId2Sid) {
        this.rawId2Sid = rawId2Sid;
    }

    private Multimap<String, String> tgResMap;
    private Multimap<String, String> wmResMap;
    private BiMap<String, String> MD5toIndex;
    private Multimap<String, String> sid2Index;
    private Multimap<String, String> sid2RawId;
    private HashMap<String, String> index2Sid;
    private HashMap<String, String> rawId2Sid;
    Multimap<String, String> sig2sids;


    public static Connection getConnection() throws FileNotFoundException, SQLException {
//        if (conn == null) {
            String driver = "com.mysql.cj.jdbc.Driver";
            conn = DriverManager.getConnection("jdbc:mysql://172.27.9.141/syj_netfood?serverTimezone=UTC", "syj", "syj123.");
//        }
        return conn;
    }

    public static Connection getWriteConnection() throws FileNotFoundException, SQLException {
//        if (conn == null) {
            String driver = "com.mysql.cj.jdbc.Driver";
            conn = DriverManager.getConnection("jdbc:mysql://172.27.9.141/syj_netfood?serverTimezone=UTC", "syj", "syj123.");
//        }
        return conn;
    }

    public static void close() throws SQLException {
        conn.close();
    }
    private int month ;
    DuplicatedPermitSearchRemoteWm() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger("org.mongodb.driver");
        rootLogger.setLevel(Level.OFF);
        operator = new MongoOperator();
        operator.connect();
        MD5toIndex = HashBiMap.create();
        sid2Index = ArrayListMultimap.create();
        sid2RawId = ArrayListMultimap.create();
        sig2sids = ArrayListMultimap.create();

        index2Sid = new HashMap<>();
        rawId2Sid = new HashMap<>();
    }

    public void init(int month) {
        this.month = month;
        try {
            wmResMap = getWmResMap(month);
            //test
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (SQLException e2) {
            e2.printStackTrace();
        }
        System.out.println("data refetched");
        System.out.println("wm sid size" + wmResMap.size());
        System.out.println("wm md5 size" + wmResMap.keySet().size());

    }

    public Multimap<String, String> getBase64(List<String> seckeys) {
        Multimap<String, String> resMap = ArrayListMultimap.create();
        Map<String, String> map = new HashMap<>();
        int counter = 0;
//        for (String sid : new ArrayList<String>(this.tgResMap.keySet())) {

        this.init(month);
        for (String sid : seckeys) {
//        for (String sid : new ArrayList<String>(tgResMap.keySet()).subList(1,151)) {//test
            for (String md5 : wmResMap.get(sid)) {
                try {
                    map = operator.getPic("certificate_cache", md5);
                    resMap.put(sid, map.get("base64"));
                }catch (NullPointerException e){
                    System.err.println(sid +"No md5s");
                }
            }
            if (counter % 1000 == 0) {
                System.out.println((double) counter / (double) seckeys.size() * 100 + "%");
            }
            counter++;
        }
        System.out.println("base64 fetched..");
        return resMap;
    }


    public static Multimap<String, String> getWmResMap(int month) throws FileNotFoundException, SQLException {
        Multimap<String, String> tgResMap = ArrayListMultimap.create();
        String sql = "select sid,md5_code from wm_shop_certificate_" + String.valueOf(month);
        PreparedStatement pst = DuplicatedPermitSearchRemoteWm.getConnection().prepareStatement(sql);
        ResultSet rs = pst.executeQuery();
        while (rs.next()) {
            String sid = rs.getString(1);
            if (sid != null) {
                String md5 = rs.getString(2);
                tgResMap.put(sid, md5);
            }
        }
        return tgResMap;
    }


    public List<Triple<String, String, String>[]> bucketingClassifying(Multimap<String, String> sig2base64s) {
        List<String> base64s = new ArrayList<>();
        for (String sid : sig2base64s.keySet()) {
            for (String base64 : sig2base64s.get(sid)) {
                base64s.add(sid + "|" + base64);
            }
        }
        sig2base64s.clear();
//        base64s = base64s.subList(0,51);
        RestTemplate restTemplate1 = new RestTemplate();
        int ThreadNum = 10;
        // bucketing
        List<Triple<String, String, String>[]> base64sBuckets = new ArrayList<>();
        Triple<String, String, String>[] tempBucket = new Triple[ThreadNum];
        System.out.println("base64 bucketing and classify started");
        for (int i = 0; i < base64s.size(); i++) {

            String sid = base64s.get(i).split("\\|")[0];
            String base64 = base64s.get(i).split("\\|")[1];
            String classifyRes = null;
            String cate;

            /////////////////
            // classifying
            //////////////////
            MultiValueMap<String, String> requestEntity = new LinkedMultiValueMap<String, String>();
            requestEntity.set("imagestr", base64);
//            String url1 = "http://172.27.9.145:5009/extract_upload";
            String url = "http://172.27.2.99:5009/extract_upload";
            try {
                classifyRes = restTemplate1.postForObject(url, requestEntity, String.class);
            } catch (Exception e) {
                requestEntity.get("imagestr");
            }
            JSONObject jso = JSONObject.parseObject(classifyRes);
            //signature write
            if (jso != null) {
                if (jso.containsKey("category")) {
                    cate = jso.get("category").toString();
                } else {
                    cate = "null";
                }
            } else {
                cate = "null";
            }
            ///////////////////
            try {

                tempBucket[i % ThreadNum] = new Triple<>(cate, base64, sid);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if ((i != 0 && i % ThreadNum == ThreadNum - 1)) {
                base64sBuckets.add(tempBucket.clone());
                if (base64s.size() - base64sBuckets.size() * ThreadNum < ThreadNum)
                    tempBucket = new Triple[base64s.size() - base64sBuckets.size() * ThreadNum];
                else
                    tempBucket = new Triple[ThreadNum];
            }
            if (i == base64s.size() - 1) {
                base64sBuckets.add(tempBucket.clone());

                tempBucket = new Triple[ThreadNum];
            }
            if (i % 1000 == 0) {
                System.out.println((double) i / (double) base64s.size() * 100 + "%");
            }
        }
        System.out.println("base64 bucketing completed");
        return base64sBuckets;
    }

    public List<String> getSigBatch(List<Triple> pairslist) {

        int ThreadNum = pairslist.size();
        Triple[] pairs = new Triple[ThreadNum];
        for (int i = 0; i < pairslist.size(); i++) {
            pairs[i] = (Triple) pairslist.get(i);
        }


        ExecutorService pool = Executors.newFixedThreadPool(ThreadNum);
        Callable[] ocr6__ = new Callable[ThreadNum];
        for (int i = 0; i < ThreadNum; i++) {
            ocr6__[i] = new RuleOCR6__(pairs[i].getS().toString(), pairs[i].getV().toString(), pairs[i].getT().toString());
        }
        Future[] futures = new Future[ThreadNum];

        for (int i = 0; i < ThreadNum; i++) {
            futures[i] = pool.submit(ocr6__[i]);
        }
        List<String> ress = new ArrayList<>();
        for (int i = 0; i < ThreadNum; i++) {
            String fget = "";
            try {
                fget = (String) futures[i].get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e1) {
                e1.printStackTrace();
            }
            ress.add((String) fget);
        }
        pool.shutdown();
        List<String> results = new ArrayList<>();
        for (String res : ress) {
            String oid = res.split("\\|")[1];
            String restr = res.split("\\|")[0];
            String clasz = res.split("\\|")[2];
            String sigNature = "";
            if (clasz.startsWith("class2")) {
                try {
                    sigNature = singlePicProcess.RuleOCR6_over(restr);
                } catch (Exception e) {
                    System.err.println("err occurs");
                    e.printStackTrace();
                }
            }

            results.add(sigNature + "|" + oid + "|" + clasz);
        }


        return results;

    }
    public JSONObject start() throws Exception{
        int size= wmResMap.keySet().size();
        int partNum=5000;
        int sec = size/partNum;
        int sec_ = size%partNum;
        Multimap<String,String> wmResMap = getWmResMap();
        for(int i=0;i<sec;i++){
            List<String> secMapKeys = new ArrayList<>();
            if(i!=sec-1){
                for(int j=partNum*(i);j<(i+1)*partNum;j++){
                    try {
                        secMapKeys.add(new ArrayList<>(wmResMap.keySet()).get(j));
                    }catch (Exception e){
                        System.out.println(j);
                        System.out.println(i);

                    }
                }
            }
            else {
                for(int j=partNum*(i);j<i*partNum+sec_;j++){
                    try {
                    secMapKeys.add(new ArrayList<>(wmResMap.keySet()).get(j));
                    }catch (Exception e){
                        System.out.println(j);
                        System.out.println(i);
                        System.out.println(sec_);

                    }
                }
            }
            System.out.println("sec"+(i+1)+"/"+(sec+1)+" start");
            System.out.println("sec"+"size:"+size);
            this.dpsr(secMapKeys,(i+1));
            //
            System.currentTimeMillis();
        }
        return results();
    }
    public void dpsr(List<String> keys,int sec)  {

        System.setProperty("DEBUG.MONGO", "false");

        int batchCounter = 0;

        Multimap<String, String> wmBase64s = this.getBase64(keys);
        List<Triple<String, String, String>[]> signitruesBuckets = this.bucketingClassifying(wmBase64s);
        //get signitrues
        System.out.println("wm buckets size" + signitruesBuckets.size());

        System.out.println("base64 get signiture started");
        for (Triple[] base64sBucket : signitruesBuckets) {
            List<Triple> pairs = new ArrayList<>();
            for (Triple pair : base64sBucket) {
                pairs.add(pair);
            }

            List<String> sigs = null;
            try {
                sigs = getSigBatch(pairs);
            } catch (Exception e) {
                System.err.println("sig err!");
            }
            if (sigs != null)
                for (Object sigl : sigs) {

                    String sigo = (String) sigl;
                    String sig = sigo.split("\\|")[0];
                    String oid = sigo.split("\\|")[1];
//                    String clasz = sigo.split("\\|")[2];
                    boolean dup = false;
                    sig2sids.put(sig, oid);
                    batchCounter++;
                    if (batchCounter % 1000 == 0) {
                        System.out.println((double) batchCounter / (double) signitruesBuckets.size() * 100 + "%");
                    }
                }
        }
        signitruesBuckets = null;
        wmBase64s = null;
        this.setWmResMap(null);
        Date now2 = new Date();
        System.out.println("wm dpsr completed at" + now2+" "+sec);

    }
    public JSONObject results()throws Exception{
        JSONArray jsaRes2 = new JSONArray();

        jsaRes2 =  printOut(sig2sids,"wm",getWriteConnection(),this.month);
//        connection1.close();
        close();
        sig2sids.clear();


        JSONObject jsonObject = new JSONObject();
        jsonObject.put("result2", jsaRes2);
        Date now =new Date();
        System.out.println("wm dprs mission completed at"+ now);
        System.out.println(jsonObject.toString());
        return jsonObject;

    }
    public JSONArray printOut(Multimap<String, String> sig2sids,String type,Connection connection,int month){
        JSONArray jsaRes = new JSONArray();
        Multimap<String, String> sig2sidsLimit = ArrayListMultimap.create();
        for (String sig : sig2sids.keySet()) {
            if (sig2sids.get(sig).size() > 1 && sig2sids.get(sig).size()<100) {
                if (!sig.equals("锦江区幸福人家农家乐卢勇热食类食品制售nullJY25101040047197")) {
                    Multimap<String, String> bags = ArrayListMultimap.create();
                    for (String sid : sig2sids.get(sig)) {
                        sig2sidsLimit.put(sig, sid);
//                        bags.put(sid.substring(0, 1), sid);
                        bags.put(sid.substring(0, 2), sid);
                    }
                    JSONObject jso = new JSONObject();

                    //bag check
                    for (String word : bags.keySet()) {
                        if (bags.get(word).size() > 1) {
                            //write out

                            String code = sig.split("null")[1];
                            for (String sid : bags.get(word)) {
                                try {
                                    updateDPS(sid, code, type, connection,month);
                                }catch (SQLException e){
                                    System.err.println(sid +" update fail at "+type);
                                    e.printStackTrace();
                                }
                            }
                            //print out
                            jso.put("signitrue", sig);
                            JSONArray jsa = new JSONArray();
                            System.out.println(sig);
                            for (String sid : bags.get(word)) {
                                System.out.println(sid);
                                JSONObject jsoless = new JSONObject();
                                jsoless.put("duplicated_permit_shop", sid);
                                jsa.add(jsoless);
                            }
                            jso.put("shops", jsa);

                        }
                    }
                    jsaRes.add(jso);
                }
            }
        }
        return jsaRes;
    }



    public void updateDPS(String sid,String code,String type,Connection connection,int month)  throws SQLException{
        String sql = String.format("UPDATE "+"analysis_"+type+"_shop"+" set"+" duplicate_premit=1,premit_code ='%s' where sid = '"+sid+
                "' and month = "+month,
                code);
        PreparedStatement pst2 = connection.prepareStatement(sql);
        pst2.execute();
    }



    public static void main(String[] arg) throws Exception {
        DuplicatedPermitSearchRemoteWm dpsRemote = new DuplicatedPermitSearchRemoteWm();
        dpsRemote.init(5);
//        dpsRemote.dpsr();
    }
}

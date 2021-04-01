package com.cicdi.jcli.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.cicdi.jcli.model.Tuple;
import com.platon.bech32.Bech32;
import com.platon.crypto.WalletFile;
import com.platon.utils.Files;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author haypo
 * @date 2021/3/1
 */
@Slf4j
public class AddressUtil {
    /**
     * 旧地址格式的正则表达式
     */
    private static final Pattern OLD_ADDRESS_PATTERN = Pattern.compile(".*address\":[\\s]*\".*");

    /**
     * 格式化地址，例如将lax开头的地址转换为atp开头的，方便对多种钱包文件实现兼容
     *
     * @param address 原始地址
     * @param hrp     地址前缀
     * @return 符合地址前缀的格式化的地址
     */
    public static String formatHrpAddress(String address, String hrp) {
        if (address.startsWith(hrp)) {
            log.info("钱包文件地址：{}，匹配hrp：{}", address, hrp);
            return address;
        }
        log.warn("钱包文件地址：{}，不匹配hrp：{}，将自动进行转换！", address, hrp);
        try {
            String hexAddress = Bech32.addressDecodeHex(address);
            return Bech32.addressEncode(hrp, hexAddress);
        } catch (Exception e) {
            return Bech32.addressEncode(hrp, address);
        }
    }

    /**
     * 在jar所在目录查找匹配地址的钱包文件
     *
     * @param hrp     hrp值
     * @param address 地址
     * @return 钱包文件
     * @throws FileNotFoundException 未找到钱包文件
     */
    public static File getFileFromAddress(String hrp, String address) throws FileNotFoundException {
        if (new File(address).isFile()) {
            return new File(address);
        }
        List<Tuple<String, File>> tuples = readAddressFileFromDir(hrp, "./");
        for (Tuple<String, File> t : tuples) {
            if (t.getA().equals(address)) {
                return t.getB();
            }
        }
        throw new FileNotFoundException("can not find wallet file matches address: " + address);
    }

    public static String getFilenameFromAddress(String hrp, String address) throws FileNotFoundException {
        List<Tuple<String, String>> tuples = readAddressFromDir(hrp);
        for (Tuple<String, String> t : tuples) {
            if (t.getA().equals(address)) {
                return t.getB();
            }
        }
        throw new FileNotFoundException("can not find wallet file matches address: " + address);
    }

    /**
     * 从dir中读取钱包文件
     *
     * @param hrp hrp值
     * @param dir 钱包路径
     * @return Tuple<String, File>列表，地址-钱包文件
     */
    public static List<Tuple<String, File>> readAddressFileFromDir(String hrp, String dir) {
        File root = new File(dir);
        List<Tuple<String, File>> addressFileTuple = new ArrayList<>();
        File[] files = root.listFiles(file -> file.isFile() && file.getName().endsWith(".json") ||
                file.getName().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    addressFileTuple.add(Tuple.create(
                            readAddressFromFile(file, hrp),
                            file));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return addressFileTuple;
    }

    /**
     * Tuple<String, String> address,filename
     * 从jar所在文件夹下读取钱包地址和钱包名称
     *
     * @param hrp hrp值
     * @return 钱包地址, 钱包名称的元组列表
     */
    public static List<Tuple<String, String>> readAddressFromDir(String hrp) {
        File root = new File("./");
        List<Tuple<String, String>> addressList = new ArrayList<>();
        File[] files = root.listFiles(file -> file.isFile() && file.getName().contains("json") ||
                file.getName().contains("JSON"));
        if (files != null) {
            for (File file : files) {
                try {
                    addressList.add(Tuple.create(
                            readAddressFromFile(file, hrp),
                            file.getName()));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return addressList;
    }

    /**
     * 根据address读取地址
     *
     * @param address 地址或钱包json
     * @param hrp     hrp值
     * @return 地址
     * @throws IOException io异常
     */
    public static String readAddress(String address, String hrp) throws IOException {
        if (new File(address).isFile()) {
            return readAddressFromFile(new File(address), hrp);
        }
        return formatHrpAddress(address, hrp);
    }

    private static String readAddressFromFile(File file, String hrp) throws IOException {
        String fileContent = Files.readString(file);
        fileContent = fileContent.replaceAll(WalletUtil.MAIN_TEST_ADDRESS_REGEX, "\"address\": \"$1\"");
        WalletFile walletFile = JSON.parseObject(fileContent, WalletFile.class);
        return formatHrpAddress(walletFile.getAddress(), hrp);
    }
}

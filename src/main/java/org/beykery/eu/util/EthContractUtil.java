package org.beykery.eu.util;

import com.github.ferstl.streams.ParallelIntStreamSupport;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.beykery.eu.event.LogEvent;
import org.web3j.abi.*;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.*;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.tx.Contract;
import org.web3j.utils.Async;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * eth合约工具
 */
@Slf4j
public class EthContractUtil {

    /**
     * default from address for eth call
     */
    public static final String DEFAULT_FROM = "0x8aCc161acB2626505755bBF36184841B8c099806";
    /**
     * 0 address
     */
    public static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    /**
     * pool for parallel
     */
    private static ForkJoinPool POOL;

    /**
     * 合约函数call
     *
     * @param function        合约方法
     * @param contractAddress 合约地址
     * @return 函数结果
     * @throws Exception
     */
    public static List<Type> call(Web3j web3j, Function function, String contractAddress) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(DEFAULT_FROM, contractAddress, encodedFunction), DefaultBlockParameterName.LATEST
                )
                .send();
        if (response.hasError()) {
            throw new RuntimeException(response.getError().getCode() + " : " + response.getError().getMessage());
        }
        String res = response.getValue();
        List<Type> ts = FunctionReturnDecoder.decode(res, function.getOutputParameters());
        return ts;
    }

    /**
     * 查看交易状态
     *
     * @param hash
     * @return
     * @throws IOException
     */
    public static TransactionReceipt transactionReceipt(Web3j web3j, String hash) throws IOException {
        EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(hash).send();
        return receipt.getResult();
    }

    /**
     * transaction
     *
     * @param web3j
     * @param hash
     * @return
     */
    public static org.web3j.protocol.core.methods.response.Transaction transaction(Web3j web3j, String hash) throws Exception {
        EthTransaction et = web3j.ethGetTransactionByHash(hash).send();
        return et.getTransaction().get();
    }

    /**
     * event for receipt
     *
     * @param receipt
     * @param events
     * @return
     */
    public static List<LogEvent> events(TransactionReceipt receipt, List<Event> events) {
        List<Log> logs = receipt.getLogs();
        if (logs != null) {
            Map<String, Event> signatures = new HashMap<>();
            events.forEach(item -> {
                String encodedEventSignature = getTopic(item);
                signatures.put(encodedEventSignature, item);
            });
            List<LogEvent> es = logs.stream().map(item -> {
                String topic = item.getTopics().get(0);
                Event event = signatures.get(topic);
                if (event != null) {
                    String tx = item.getTransactionHash().toLowerCase();           // tx hash
                    BigInteger blockNumber = item.getBlockNumber();                // block number
                    BigInteger lidx = item.getLogIndex();                          // log index
                    String contractAddress = item.getAddress().toLowerCase();      // contract address

                    EventValues values = Contract.staticExtractEventParameters(event, item);

                    LogEvent le = LogEvent.builder()
                            .event(event)
                            .transactionHash(tx)
                            .blockNumber(blockNumber.longValue())
                            .logIndex(lidx.longValue())
                            .contract(contractAddress)
                            .indexedValues(values.getIndexedValues())
                            .nonIndexedValues(values.getNonIndexedValues())
                            .build();
                    return le;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            return es;
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * events for tx
     *
     * @param web3j
     * @param hash
     * @param events
     * @return
     * @throws IOException
     */
    public static List<LogEvent> events(Web3j web3j, String hash, List<Event> events) throws IOException {
        TransactionReceipt receipt = transactionReceipt(web3j, hash);
        return events(receipt, events);
    }

    /**
     * 求某一高度交易集合
     *
     * @param web3j
     * @param h
     * @return
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> transactions(Web3j web3j, BigInteger h) throws IOException {
        EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(h), true).send();
        EthBlock.Block b = block.getResult();
        List<EthBlock.TransactionResult> ts = b.getTransactions();
        return ts.stream().map(item -> {
            EthBlock.TransactionObject to = (EthBlock.TransactionObject) item;
            return to.get();
        }).collect(Collectors.toList());
    }

    /**
     * 查询合约状态
     * org.web3j.abi.datatypes 中的类型
     *
     * @param contractAddress 合约地址
     * @param funname         方法名
     * @param inputs          输入
     * @param outputs         输出
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static List<Type> call(
            Web3j web3j,
            String contractAddress,
            String funname,
            List<Type> inputs,
            List<TypeReference<?>> outputs
    ) throws Exception {
        Function function = new Function(funname, inputs, outputs);
        return call(web3j, function, contractAddress);
    }

    /**
     * balance of eip20 contract
     *
     * @param web3j
     * @param userAddress
     * @param contractAddress
     * @return
     */
    public static BigInteger balanceOf(Web3j web3j, String userAddress, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "balanceOf", Collections.singletonList(new Address(userAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return ((Uint256) ret.get(0)).getValue();
        }
        return null;
    }

    /**
     * 主币余额
     *
     * @param web3j
     * @param userAddress 用户地址（外部地址或合约地址）
     * @return
     */
    public static BigInteger balance(Web3j web3j, String userAddress) throws Exception {
        EthGetBalance b = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send();
        return b.getBalance();
    }

    /**
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static Uint256 totalSupply(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "totalSupply", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return (Uint256) ret.get(0);
        }
        return null;
    }

    /**
     * decimals for eip20 contract
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static BigInteger decimals(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "decimals", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        if (!ret.isEmpty()) {
            return ((Uint256) ret.get(0)).getValue();
        }
        return null;
    }

    /**
     * balance for eip20 with decimals
     *
     * @param web3j
     * @param userAddress
     * @param contractAddress
     * @return
     */
    public static double balanceOfWithDecimals(Web3j web3j, String userAddress, String contractAddress) throws Exception {
        BigInteger balance = balanceOf(web3j, userAddress, contractAddress);
        if (balance != null) {
            BigInteger decimals = decimals(web3j, contractAddress);
            if (decimals != null) {
                int d = decimals.intValue();
                return numberWithDecimals(balance, d, 0);
            }
        }
        return 0;
    }

    /**
     * name of contract
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static String name(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "name", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));
        if (!ret.isEmpty()) {
            Utf8String name = (Utf8String) ret.get(0);
            return name.getValue();
        }
        return null;
    }

    /**
     * symbols
     *
     * @param web3j
     * @param contractAddress
     * @return
     */
    public static String symbol(Web3j web3j, String contractAddress) throws Exception {
        List<Type> ret = call(web3j, contractAddress, "symbol", Collections.EMPTY_LIST,
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));
        if (!ret.isEmpty()) {
            Utf8String symbol = (Utf8String) ret.get(0);
            return symbol.getValue();
        }
        return null;
    }

    /**
     * allowance
     *
     * @param web3j
     * @param contractAddress
     * @return
     * @throws Exception
     */
    public static BigInteger allowance(Web3j web3j, String contractAddress, String owner, String spender) throws Exception {
        List<Type> input = new ArrayList<>();
        input.add(new Address(owner));
        input.add(new Address(spender));
        List<Type> ret = call(web3j, contractAddress, "allowance", input,
                Collections.singletonList(new TypeReference<Uint>() {
                }));
        if (!ret.isEmpty()) {
            Uint amount = (Uint) ret.get(0);
            return amount.getValue();
        }
        return null;
    }

    /**
     * @param v
     * @param decimals
     * @return
     */
    public static double numberWithDecimals(Uint256 v, int decimals) {
        return numberWithDecimals(v.getValue(), decimals, 0);
    }

    /**
     * number with decimals
     *
     * @param v
     * @param decimals
     * @return
     */
    public static double numberWithDecimals(BigInteger v, int decimals) {
        return numberWithDecimals(v, decimals, 0);
    }

    /**
     * @param v         value
     * @param decimals  小数位数
     * @param precision 放大次数
     * @return
     */
    public static double numberWithDecimals(BigInteger v, int decimals, int precision) {
        BigDecimal bd = BigDecimal.valueOf(10);
        bd = bd.pow(decimals);
        BigDecimal ret;
        if (precision > 0) {
            ret = new BigDecimal(v.multiply(BigInteger.valueOf(10).pow(precision))).divide(bd, decimals, RoundingMode.DOWN);
        } else {
            ret = new BigDecimal(v).divide(bd, decimals, RoundingMode.DOWN);
        }
        return ret.doubleValue();
    }

    /**
     * chain id
     *
     * @param web3j
     * @return
     */
    public static long chainId(Web3j web3j) throws IOException {
        EthChainId ret = web3j.ethChainId().send();
        return ret.getId();
    }

    /**
     * batch
     *
     * @param web3j
     * @return
     */
    public static BatchRequest batchRequest(Web3j web3j) {
        return web3j.newBatch();
    }

    /**
     * batch
     *
     * @param web3j
     * @param fs
     */
    public static List<List<Type>> batch(Web3j web3j, String contract, Function... fs) throws Exception {
        List<List<Type>> list = new ArrayList<>();
        BatchRequest batchRequest = web3j.newBatch();
        Stream.of(fs).forEach(item -> {
            String encode = FunctionEncoder.encode(item);
            Request<?, EthCall> call = web3j.ethCall(Transaction.createEthCallTransaction(DEFAULT_FROM, contract, encode), DefaultBlockParameterName.LATEST);
            batchRequest.add(call);
        });
        List<? extends Response<?>> responses = batchRequest.send().getResponses();
        Iterator<? extends Response<?>> it = responses.iterator();
        Stream.of(fs).forEach(item -> {
            Response<?> res = it.next();
            String hex = res.getResult().toString();
            List<Type> ret = FunctionReturnDecoder.decode(hex, item.getOutputParameters());
            list.add(ret);
        });
        return list;
    }

    /**
     * batch call
     *
     * @param web3j
     * @param contract
     * @param calls
     * @return
     */
    public static List<List<Type>> batch(Web3j web3j, String contract, RemoteFunctionCall... calls) throws IOException {
        List<List<Type>> list = new ArrayList<>();
        BatchRequest batchRequest = web3j.newBatch();
        Stream.of(calls).forEach(item -> {
            String encode = item.encodeFunctionCall();
            Request<?, EthCall> call = web3j.ethCall(Transaction.createEthCallTransaction(DEFAULT_FROM, contract, encode), DefaultBlockParameterName.LATEST);
            batchRequest.add(call);
        });
        List<? extends Response<?>> responses = batchRequest.send().getResponses();
        Iterator<? extends Response<?>> it = responses.iterator();
        Stream.of(calls).forEach(item -> {
            Response<?> res = it.next();
            String hex = res.getResult().toString();
            List<Type> ret = item.decodeFunctionResponse(hex);
            list.add(ret);
        });
        return list;
    }

    /**
     * code for function
     *
     * @param fun function
     * @return code
     */
    public static String encodedFunction(Function fun) {
        String encodedFunction = FunctionEncoder.encode(fun);
        return encodedFunction;
    }

    /**
     * byte32
     *
     * @param t
     * @return
     */
    public static Bytes32 formatBytes32String(String t) {
        byte[] temp = t.getBytes();
        int len = Math.min(temp.length, 32);
        byte[] target = new byte[32];
        System.arraycopy(temp, 0, target, 0, len);
        return new Bytes32(target);
    }

    /**
     * 默认web3j
     *
     * @return
     */
    public static Web3j getWeb3j() {
        String nodeUrl = "https://mainnet.infura.io/v3/ac6e4a09bef34cf494d1941d1bc561b6";
        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        return web3j;
    }

    /**
     * for web3j
     *
     * @param nodes
     * @return
     */
    public static Web3j getWeb3j(List<String> nodes) {
        Web3jService ws = new EuHttpService(nodes);
        Web3j web3j = Web3j.build(ws, 3, Async.defaultExecutorService());
        return web3j;
    }

    /**
     * for web3j
     *
     * @param node
     * @return
     */
    public static Web3j getWeb3j(String node) throws ConnectException {
        return getWeb3j(node, 3);
    }

    /**
     * 指定interval
     *
     * @param node
     * @param pollingInterval
     * @return
     */
    public static Web3j getWeb3j(String node, long pollingInterval) throws ConnectException {
        Web3jService ws;
        if (node.startsWith("ws")) {
            ws = new WebSocketService(node, false);
            ((WebSocketService) ws).connect();
        } else {
            ws = new EuHttpService(node);
        }
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
        return web3j;
    }

    /**
     * nodes with polling interval
     *
     * @param nodes
     * @param pollingInterval
     * @return
     * @throws ConnectException
     */
    public static Web3j getWeb3j(List<String> nodes, long pollingInterval) {
        Web3jService ws = new EuHttpService(nodes);
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
        return web3j;
    }

    /**
     * web3j
     *
     * @param okClient
     * @param pollingInterval
     * @return
     */
    public static Web3j getWeb3j(String node, OkHttpClient okClient, long pollingInterval) throws ConnectException {
        Web3jService ws;
        if (node.startsWith("ws")) {
            ws = new WebSocketService(node, false);
            ((WebSocketService) ws).connect();
        } else {
            ws = new EuHttpService(node, okClient);
        }
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
        return web3j;
    }

    /**
     * nodes with client and polling interval
     *
     * @param nodes
     * @param okClient
     * @param pollingInterval
     * @return
     * @throws ConnectException
     */
    public static Web3j getWeb3j(List<String> nodes, OkHttpClient okClient, long pollingInterval) {
        Web3jService ws = new EuHttpService(nodes, okClient, false);
        Web3j web3j = Web3j.build(ws, pollingInterval, Async.defaultExecutorService());
        return web3j;
    }

    /**
     * one for decimals
     *
     * @param decimals
     * @return
     */
    public static BigInteger one(int decimals) {
        BigInteger bi = BigInteger.valueOf(10);
        return bi.pow(decimals);
    }

    /**
     * n for decimals
     *
     * @param decimals
     * @return
     */
    public static BigInteger n(int n, int decimals) {
        return BigInteger.valueOf(n).multiply(one(decimals));
    }

    /**
     * n for decimals
     *
     * @param n
     * @param decimals
     * @return
     */
    public static BigInteger n(double n, int decimals) {
        return BigDecimal.valueOf(n).multiply(new BigDecimal(one(decimals))).toBigInteger();
    }

    /**
     * decimals
     *
     * @param d
     * @return
     */
    public static BigDecimal decimalsToNumber(int d) {
        return new BigDecimal(one(d));
    }

    /**
     * 当前高度
     *
     * @param web3j
     * @return
     * @throws Exception
     */
    public static long currentHeight(Web3j web3j) throws Exception {
        EthBlockNumber number = web3j.ethBlockNumber().send();
        return number.getBlockNumber().longValue();
    }

    /**
     * 求contract地址
     *
     * @param address
     * @param nonce
     * @return
     */
    public static String calculateContractAddress(String address, long nonce) {
        byte[] addressAsBytes = Numeric.hexStringToByteArray(address);

        byte[] calculatedAddressAsBytes =
                Hash.sha3(RlpEncoder.encode(
                        new RlpList(
                                RlpString.create(addressAsBytes),
                                RlpString.create((nonce)))));

        calculatedAddressAsBytes = Arrays.copyOfRange(calculatedAddressAsBytes,
                12, calculatedAddressAsBytes.length);
        String calculatedAddressAsHex = Numeric.toHexString(calculatedAddressAsBytes);
        return calculatedAddressAsHex;
    }


    /**
     * checksum address
     *
     * @param address
     * @return
     */
    public static String toChecksumAddress(String address) {
        return Keys.toChecksumAddress(address);
    }

    /**
     * type 2 tx
     *
     * @param privateKey
     * @param limit
     * @param to
     * @param value
     * @param data
     * @param nonce
     * @param maxPriorityFeePerGas
     * @param maxFeePerGas
     * @param chainId
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas,
            long chainId
    ) {
        RawTransaction rawTransaction = rawTransaction(chainId, nonce, limit, to, value, data, maxPriorityFeePerGas, maxFeePerGas);
        return signTransaction(privateKey, rawTransaction, chainId);
    }

    /**
     * 签名交易
     *
     * @param privateKey 私钥
     * @param price      gas price
     * @param limit      gas limit
     * @param to         合约地址或者外部地址
     * @param value      转账的ht数额
     * @param data       合约调用函数签名
     * @return signed transaction
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce,
            long chainId
    ) {
        RawTransaction rawTransaction = rawTransaction(nonce, price, limit, to, value, data);
        return signTransaction(privateKey, rawTransaction, chainId);
    }

    /**
     * 签名交易
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param data
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce
    ) {
        return signTransaction(privateKey, price, limit, to, value, data, nonce, 0);
    }

    /**
     * address for private key
     *
     * @param privateKey 私钥
     * @return 地址
     */
    public static String address(String privateKey) {
        Credentials credentials = Credentials.create(privateKey);
        BigInteger number = credentials.getEcKeyPair().getPublicKey();
        return Keys.toChecksumAddress(Keys.getAddress(number));
    }

    /**
     * 随机私钥
     *
     * @return
     */
    public static String randomPrivate() {
        try {
            ECKeyPair ecKeyPair = Keys.createEcKeyPair();
            BigInteger privateKeyInDec = ecKeyPair.getPrivateKey();
            String pri = "0x" + privateKeyInDec.toString(16);
            return pri;
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * 组织一个raw transaction
     *
     * @param nonce nonce
     * @param price gas价格
     * @param limit gas limit
     * @param to    合约或外部地址
     * @param value 转账的ht数额
     * @param data  消息（合约调用的方法签名）
     * @return raw transaction
     */
    public static RawTransaction rawTransaction(
            BigInteger nonce,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data
    ) {
        RawTransaction tr = RawTransaction.createTransaction(nonce, price, limit, to, value, data);
        return tr;
    }

    /**
     * 组织一个raw transaction
     * eip1559
     *
     * @param nonce
     * @param limit
     * @param to
     * @param value
     * @param data
     * @return
     */
    public static RawTransaction rawTransaction(
            long chainId,
            BigInteger nonce,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas
    ) {
        RawTransaction tr = RawTransaction.createTransaction(chainId, nonce, limit, to, value, data, maxPriorityFeePerGas, maxFeePerGas);
        return tr;
    }

    /**
     * 本地签名
     *
     * @param privateKey          私钥
     * @param unsignedTransaction 待签名交易
     * @return signed transaction
     */
    public static String signTransaction(String privateKey, RawTransaction unsignedTransaction, long chainId) {
        Credentials credentials = Credentials.create(privateKey);
        byte[] signedMessage = chainId > 0 ? TransactionEncoder.signMessage(unsignedTransaction, chainId, credentials) : TransactionEncoder.signMessage(unsignedTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        return hexValue;
    }

    /**
     * 本地签名
     *
     * @param privateKey
     * @param unsignedTransaction
     * @return
     */
    public static String signTransaction(String privateKey, RawTransaction unsignedTransaction) {
        return signTransaction(privateKey, unsignedTransaction, 0);
    }

    /**
     * nonce for address
     *
     * @param address 外部地址
     * @return nonce
     */
    public static BigInteger nonce(Web3j web3j, String address) {
        return nonce(web3j, address, DefaultBlockParameterName.LATEST);
    }

    /**
     * nonce of address
     *
     * @param web3j
     * @param address
     * @param param
     * @return
     */
    public static BigInteger nonce(Web3j web3j, String address, DefaultBlockParameterName param) {
        try {
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(address, param).sendAsync().get();
            BigInteger nonce = ethGetTransactionCount.getTransactionCount();
            return nonce;
        } catch (InterruptedException | ExecutionException ex) {
            return BigInteger.valueOf(-1);
        }
    }

    /**
     * 签名并发送交易
     *
     * @param privateKey 私钥
     * @param price      gas price
     * @param limit      gas limit
     * @param to         合约或外部地址
     * @param value      转账的ht数额
     * @param data       调用合约方法签名
     * @return
     * @throws Exception
     */
    public static EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce,
            long chainId
    ) throws Exception {
        String signedTransaction = signTransaction(privateKey, price, limit, to, value, data, nonce, chainId);
        return sendSignedTransaction(web3j, signedTransaction);
    }

    /**
     * 签名并发送交易
     *
     * @param web3j
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param data
     * @param nonce
     * @return
     * @throws Exception
     */
    public static EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            String data,
            BigInteger nonce
    ) throws Exception {
        return signAndSendTransaction(web3j, privateKey, price, limit, to, value, data, nonce, 0);
    }

    /**
     * send signed transaction
     *
     * @param signedTransaction signed transaction
     * @return 结果（hash）
     */
    public static EthSendTransaction sendSignedTransaction(Web3j web3j, String signedTransaction) throws Exception {
        final EthSendTransaction result = web3j.ethSendRawTransaction(signedTransaction).send();
        return result;
    }

    /**
     * decode input data
     *
     * @param inputData
     * @param outputParameters
     * @return
     */
    public static List<Type> decodeInputData(String inputData, List<TypeReference<?>> outputParameters) {
        List<Type> result = FunctionReturnDecoder.decode(
                inputData.substring(10),
                convert(outputParameters)
        );
        return result;
    }

    /**
     * ? to Type
     *
     * @param input
     * @return
     */
    public static List<TypeReference<Type>> convert(List<TypeReference<?>> input) {
        List<TypeReference<Type>> result = new ArrayList<>(input.size());
        result.addAll(
                input.stream()
                        .map(typeReference -> (TypeReference<Type>) typeReference)
                        .collect(Collectors.toList()));
        return result;
    }

    /**
     * encode function
     *
     * @param f
     * @return
     */
    public static String encode(Function f) {
        final String encode = FunctionEncoder.encode(f);
        return encode;
    }

    /**
     * @param f
     * @return
     */
    public static String buildMethodId(Function f) {
        String signature = buildMethodSignature(f.getName(), f.getInputParameters());
        String id = buildMethodId(signature);
        return id;
    }

    /**
     * method id
     *
     * @param methodName
     * @param parameters
     * @return
     */
    public static String buildMethodId(final String methodName, final List<Type> parameters) {
        String signature = buildMethodSignature(methodName, parameters);
        String id = buildMethodId(signature);
        return id;
    }

    /**
     * method signature
     *
     * @param methodName
     * @param parameters
     * @return
     */
    public static String buildMethodSignature(final String methodName, final List<Type> parameters) {
        final StringBuilder result = new StringBuilder();
        result.append(methodName);
        result.append("(");
        final String params =
                parameters.stream().map(Type::getTypeAsString).collect(Collectors.joining(","));
        result.append(params);
        result.append(")");
        return result.toString();
    }

    /**
     * method id
     *
     * @param methodSignature
     * @return
     */
    public static String buildMethodId(final String methodSignature) {
        final byte[] input = methodSignature.getBytes();
        final byte[] hash = Hash.sha3(input);
        return Numeric.toHexString(hash).substring(0, 10);
    }

    /**
     * type 2 tx sign
     *
     * @param privateKey
     * @param limit
     * @param to
     * @param value
     * @param f
     * @param nonce
     * @param chainId
     * @param maxPriorityFeePerGas
     * @param maxFeePerGas
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger limit,
            String to,
            BigInteger value,
            Function f,
            BigInteger nonce,
            long chainId,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas
    ) {
        String data = encode(f);
        return signTransaction(privateKey, limit, to, value, data, nonce, maxPriorityFeePerGas, maxFeePerGas, chainId);
    }

    /**
     * sign function for contract
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param f
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            Function f,
            BigInteger nonce,
            long chainId
    ) {
        String data = encode(f);
        return signTransaction(privateKey, price, limit, to, value, data, nonce, chainId);
    }

    /**
     * sign function for contract
     *
     * @param privateKey
     * @param price
     * @param limit
     * @param to
     * @param value
     * @param f
     * @param nonce
     * @return
     */
    public static String signTransaction(
            String privateKey,
            BigInteger price,
            BigInteger limit,
            String to,
            BigInteger value,
            Function f,
            BigInteger nonce
    ) {
        return signTransaction(privateKey, price, limit, to, value, f, nonce, 0);
    }

    /**
     * pending tx filter id
     *
     * @param web3j
     * @return
     * @throws IOException
     */
    public static BigInteger newPendingTransactionFilterId(Web3j web3j) throws IOException {
        EthFilter filter = web3j.ethNewPendingTransactionFilter().send();
        BigInteger fid = filter.getFilterId();
        return fid;
    }

    /**
     * pending txs
     *
     * @param web3j
     * @param filterId
     * @return
     * @throws Exception
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> pendingTransactions(Web3j web3j, BigInteger filterId) throws Exception {
        return pendingTransactions(web3j, filterId, 3, 50);
    }

    /**
     * pending txs
     *
     * @param web3j
     * @param filterId
     * @param parallel
     * @return
     * @throws Exception
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> pendingTransactions(Web3j web3j, BigInteger filterId, int parallel) throws Exception {
        return pendingTransactions(web3j, filterId, parallel, 50);
    }

    /**
     * pending transactions
     *
     * @param web3j
     * @param filterId
     * @param parallel
     * @return
     */
    public static List<org.web3j.protocol.core.methods.response.Transaction> pendingTransactions(Web3j web3j, BigInteger filterId, int parallel, int batchSize) throws Exception {
        EthLog logs = web3j.ethGetFilterChanges(filterId).send();
        List<EthLog.LogResult> ls = logs.getLogs();
        if (ls != null && ls.size() > 0) {
            if (parallel > 1) {
                if (POOL == null || POOL.getParallelism() != parallel) {
                    POOL = new ForkJoinPool(parallel);
                }
            }
            batchSize = batchSize <= 0 ? 50 : batchSize;
            int group = ls.size() % batchSize == 0 ? (ls.size() / batchSize) : (ls.size() / batchSize + 1);
            if (group == 0) {
                group++;
            }
            final int finalBatchSize = batchSize;
            IntStream stream = (parallel > 1 && group > 1) ? ParallelIntStreamSupport.range(0, group, POOL) : IntStream.range(0, group);
            List<List<EthTransaction>> list = stream.mapToObj(g -> {
                int start = g * finalBatchSize;
                int end = start + finalBatchSize;
                if (end > ls.size()) {
                    end = ls.size();
                }
                BatchRequest request = batchRequest(web3j);
                for (int i = start; i < end; i++) {
                    String hash = ls.get(i).get().toString();
                    request.add(web3j.ethGetTransactionByHash(hash));
                }
                try {
                    BatchResponse response = request.send();
                    List<EthTransaction> responses = (List<EthTransaction>) response.getResponses();
                    return responses;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
            List<org.web3j.protocol.core.methods.response.Transaction> ret = new ArrayList<>();
            list.forEach(item -> item.forEach(r -> {
                org.web3j.protocol.core.methods.response.Transaction t = r.getResult();
                if (t != null) {
                    ret.add(r.getResult());
                }
            }));
            if (ret.size() != ls.size()) {
                log.warn("{} fetched with {} total pxs", ret.size(), ls.size());
            }
            ret.sort((t1, t2) -> {
                BigInteger price1 = t1.getGasPrice();
                if (price1 == null) {
                    price1 = t1.getMaxFeePerGas();
                }
                BigInteger price2 = t2.getGasPrice();
                if (price2 == null) {
                    price2 = t2.getMaxFeePerGas();
                }
                return price2.compareTo(price1);
            });
            return ret;
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * event topic
     *
     * @param event
     * @return
     */
    public static String getTopic(Event event) {
        String encodedEventSignature = EventEncoder.encode(event);
        return encodedEventSignature;
    }

    /**
     * 查找块上的事件
     *
     * @param web3j
     * @param events
     * @return
     */
    public static List<LogEvent> getLogEvents(Web3j web3j, long from, long to, List<Event> events, List<String> contracts) throws Exception {
        return getLogEvents(web3j, from, to, events, contracts, false);
    }

    /**
     * 获取tx上的log
     *
     * @param web3j
     * @param hash
     * @param events
     * @param contracts
     * @return
     * @throws IOException
     */
    public static List<LogEvent> getLogEvents(Web3j web3j, String hash, List<Event> events, List<String> contracts) throws IOException {
        EthGetTransactionReceipt er = web3j.ethGetTransactionReceipt(hash).send();
        if (er.getTransactionReceipt().isPresent()) {
            TransactionReceipt receipt = er.getTransactionReceipt().get();
            List<Log> logs = receipt.getLogs();
            if (logs.size() > 0) {
                Map<String, Event> signatures = new HashMap<>();
                events.forEach(item -> {
                    String encodedEventSignature = EventEncoder.encode(item);
                    signatures.put(encodedEventSignature, item);
                });
                Set<String> contractsFilter = (contracts == null || contracts.isEmpty()) ? null : contracts.stream().map(String::toLowerCase).collect(Collectors.toSet());
                Stream<Log> stream = logs.stream()
                        .filter(item -> {
                                    if (contractsFilter != null) {
                                        if (!contractsFilter.contains(item.getAddress())) {
                                            return false;
                                        }
                                    }
                                    String topic = item.getTopics().get(0);
                                    int size = item.getTopics().size() - 1;
                                    Event event = signatures.get(topic);
                                    List<TypeReference<Type>> indexedParams = event == null ? null : event.getIndexedParameters();
                                    if (indexedParams != null) {
                                        return size == indexedParams.size();
                                    } else {
                                        return false;
                                    }
                                }
                        );
                List<LogEvent> les = stream.map(item -> {
                    String topic = item.getTopics().get(0);
                    Event event = signatures.get(topic);

                    String tx = item.getTransactionHash().toLowerCase();           // tx hash
                    BigInteger blockNumber = item.getBlockNumber();                // block number
                    BigInteger lidx = item.getLogIndex();                          // log index
                    String contractAddress = item.getAddress().toLowerCase();      // contract address

                    EventValues values = Contract.staticExtractEventParameters(event, item);

                    // 通知listener
                    LogEvent le = LogEvent.builder()
                            .event(event)
                            .transactionHash(tx)
                            .blockNumber(blockNumber.longValue())
                            .logIndex(lidx.longValue())
                            .contract(contractAddress)
                            .indexedValues(values.getIndexedValues())
                            .nonIndexedValues(values.getNonIndexedValues())
                            .build();
                    return le;
                }).collect(Collectors.toList());

                return les;
            }
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * 分析log
     *
     * @param web3j
     * @param from
     * @param to
     * @param events
     * @param contracts
     * @param logFromTx
     * @return
     * @throws IOException
     */
    public static List<LogEvent> getLogEvents(Web3j web3j, long from, long to, List<Event> events, List<String> contracts, boolean logFromTx) throws Exception {
        if (logFromTx) {
            List<LogEvent> logs = Collections.synchronizedList(new ArrayList<>());
            while (from <= to) {
                EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(from)), false).send();
                List<EthBlock.TransactionResult> transactionResults = block.getBlock().getTransactions();
                List<String> hashes = transactionResults.size() > 0 ? transactionResults.stream().map(item -> item.get().toString()).collect(Collectors.toList()) : null;
                if (hashes != null) {
                    hashes.parallelStream().forEach(hash -> {
                        try {
                            List<LogEvent> es = getLogEvents(web3j, hash, events, contracts);
                            logs.addAll(es);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }
                from++;
            }
            if (logs.size() > 0) {
                logs.sort((o1, o2) -> {
                    long ret = o1.getBlockNumber() - o2.getBlockNumber();
                    if (ret > 0) {
                        return 1;
                    } else if (ret < 0) {
                        return -1;
                    } else {
                        ret = o1.getLogIndex() - o2.getLogIndex();
                        if (ret > 0) {
                            return 1;
                        } else if (ret < 0) {
                            return -1;
                        } else {
                            return 0;
                        }
                    }
                });
            }
            return logs;
        }
        Map<String, Event> signatures = new HashMap<>();
        events.forEach(item -> {
            String encodedEventSignature = EventEncoder.encode(item);
            signatures.put(encodedEventSignature, item);
        });
        org.web3j.protocol.core.methods.request.EthFilter filter = new org.web3j.protocol.core.methods.request.EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
                contracts == null ? Collections.EMPTY_LIST : contracts
        );
        Set<String> topics = signatures.keySet();
        filter.addOptionalTopics(topics.toArray(new String[0]));
        EthLog el = web3j.ethGetLogs(filter).send();
        if (el == null || el.hasError() || el.getLogs() == null) {
            throw new RuntimeException("can not fetch logs");
        }
        List<EthLog.LogResult> lr = el.getLogs();
        if (lr != null) {
            List<Log> logs = lr.stream().map(item -> {
                EthLog.LogObject lo = (EthLog.LogObject) item.get();
                Log log = lo.get();
                return log;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            if (logs.size() > 0) {
                // ParallelStreamSupport.parallelStream(logs, Streams.POOL)
                Stream<Log> stream = logs.stream()
                        .filter(item -> {
                            String topic = item.getTopics().get(0);
                            int size = item.getTopics().size() - 1;
                            Event event = signatures.get(topic);
                            List<TypeReference<Type>> indexedParams = event == null ? null : event.getIndexedParameters();
                            if (indexedParams != null) {
                                return size == indexedParams.size();
                            } else {
                                return false;
                            }
                        });
                List<LogEvent> les = stream.map(item -> {
                    String topic = item.getTopics().get(0);
                    Event event = signatures.get(topic);

                    String tx = item.getTransactionHash().toLowerCase();           // tx hash
                    BigInteger blockNumber = item.getBlockNumber();                // block number
                    BigInteger lidx = item.getLogIndex();                          // log index
                    String contractAddress = item.getAddress().toLowerCase();      // contract address

                    EventValues values = Contract.staticExtractEventParameters(event, item);

                    // build log event
                    LogEvent le = LogEvent.builder()
                            .event(event)
                            .transactionHash(tx)
                            .blockNumber(blockNumber.longValue())
                            .logIndex(lidx.longValue())
                            .contract(contractAddress)
                            .indexedValues(values.getIndexedValues())
                            .nonIndexedValues(values.getNonIndexedValues())
                            .build();
                    return le;
                }).collect(Collectors.toList());

                return les;
            }
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * get address for signed message
     *
     * @param signedMessageInHex
     * @param originalMessage
     * @return
     * @throws SignatureException
     */
    public static String getAddressUsedToSignHashedMessage(
            String signedMessageInHex,
            String originalMessage
    ) throws SignatureException {
        if (signedMessageInHex.startsWith("0x")) {
            signedMessageInHex = signedMessageInHex.substring(2);
        }

        // No need to prepend these strings with 0x because
        // Numeric.hexStringToByteArray() accepts both formats
        String r = signedMessageInHex.substring(0, 64);
        String s = signedMessageInHex.substring(64, 128);
        String v = signedMessageInHex.substring(128, 130);

        // Using Sign.signedPrefixedMessageToKey for EIP-712 compliant signatures.
        String pubkey = Sign.signedPrefixedMessageToKey(originalMessage.getBytes(),
                        new Sign.SignatureData(
                                Numeric.hexStringToByteArray(v)[0],
                                Numeric.hexStringToByteArray(r),
                                Numeric.hexStringToByteArray(s)))
                .toString(16);
        return Keys.getAddress(pubkey);
    }
}

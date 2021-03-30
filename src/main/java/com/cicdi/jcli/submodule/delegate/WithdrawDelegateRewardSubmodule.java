package com.cicdi.jcli.submodule.delegate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.cicdi.jcli.contractx.RewardContractX;
import com.cicdi.jcli.converter.BigIntegerConverter;
import com.cicdi.jcli.model.NodeConfigModel;
import com.cicdi.jcli.service.FastHttpService;
import com.cicdi.jcli.submodule.AbstractSimpleSubmodule;
import com.cicdi.jcli.template.BaseTemplate4Serialize;
import com.cicdi.jcli.util.*;
import com.platon.contracts.ppos.utils.EncoderUtils;
import com.platon.crypto.Credentials;
import com.platon.crypto.WalletUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.TransactionReceipt;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigInteger;
import java.util.Collections;

/**
 * 提取委托奖励
 *
 * @author haypo
 * @date 2021/1/8
 */
@SuppressWarnings("unused")
@Slf4j
@Parameters(commandNames = "delegate_withdrawDelegateReward", commandDescription = "提取委托奖励")
public class WithdrawDelegateRewardSubmodule extends AbstractSimpleSubmodule {
    @Parameter(names = {"--offline", "-o"}, description = "在线交易或者离线交易. 不输入默认为在线交易, 并生成二维码图片放置在桌面上，提供ATON离线扫码签名")
    protected boolean offline;
    @Parameter(names = {"--gasLimit", "-gasLimit"}, description = "gas用量限制", converter = BigIntegerConverter.class)
    protected BigInteger gasLimit = Common.MID_GAS_LIMIT;
    @Parameter(names = {"--gasPrice", "-gasPrice"}, description = "gas价格", converter = BigIntegerConverter.class)
    protected BigInteger gasPrice = Common.MID_GAS_PRICE;
    @Parameter(names = {"--address", "-address", "-d"}, description = "发送交易地址或者名称.json", required = true)
    protected String address;
    @Parameter(names = {"--fast", "-fast", "-f"}, description = "是否使用快速发送功能，默认不使用")
    protected boolean fast;

    public boolean isOnline() {
        return !offline && new File(address).isFile();
    }

    @Override
    public String run(JCommander jc, String... argv) throws Exception {
        GasPriceUtil.verifyGasPrice(gasPrice);

        NodeConfigModel nodeConfigModel = ConfigUtil.readConfig(config);
        Web3j web3j = Web3j.build(new FastHttpService(nodeConfigModel.getRpcAddress()));
        String hrpAddress = AddressUtil.readAddress(address, nodeConfigModel.getHrp());

        //若待领取收益为0则提醒
        if (WalletUtil.getDelegateReward(web3j, nodeConfigModel.getHrp(), hrpAddress).compareTo(BigInteger.ZERO) == 0) {
            log.warn("Un-withdrew delegate reward is zero, continue? Y/N");
            if (!StringUtil.readYesOrNo()) {
                return Common.CANCEL_STR;
            }
        }

        if (isOnline()) {
            File addressFile = AddressUtil.getFileFromAddress(nodeConfigModel.getHrp(), address);
            Credentials credentials = WalletUtil.loadCredentials(StringUtil.readPassword(), addressFile,nodeConfigModel.getHrp());
            RewardContractX rc = RewardContractX.load(web3j, credentials, nodeConfigModel.getChainId(), nodeConfigModel.getHrp());
            if (fast) {
                //快速发送交易
                rc.fastWithdrawDelegateReward(
                        nodeConfigModel.getRpcAddress(),
                        credentials,
                        nodeConfigModel.getChainId(), gasLimit, gasPrice,
                        NonceUtil.getNonce(web3j, hrpAddress, nodeConfigModel.getHrp())
                );
                return Common.SUCCESS_STR;
            } else {
                TransactionReceipt transactionReceipt = rc.withdrawDelegateReward(ConvertUtil.createGasProvider(gasLimit, gasPrice)).send().getTransactionReceipt();
                return TransactionReceiptUtil.handleTxReceipt(transactionReceipt);
            }
        } else {
            BaseTemplate4Serialize baseTemplate4Serialize = new BaseTemplate4Serialize(
                    hrpAddress,
                    Collections.singletonList(NetworkParametersUtil.getPposContractAddressOfReward(nodeConfigModel.getHrp())),
                    EncoderUtils.functionEncoder(RewardContractX.createWithdrawDelegateRewardFunction()),
                    NonceUtil.getNonce(web3j, hrpAddress, nodeConfigModel.getHrp()),
                    BigInteger.ZERO,
                    nodeConfigModel.getChainId(),
                    gasLimit,
                    gasPrice,
                    fast
            );
            return QrUtil.save2QrCodeImage(getQrCodeImagePrefix(), baseTemplate4Serialize);
        }
    }
}

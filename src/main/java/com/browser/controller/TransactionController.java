package com.browser.controller;

import com.browser.dao.entity.*;
import com.browser.service.*;
import com.browser.tools.Constant;
import com.browser.wallet.PrecisionUtils;
import com.browser.wallet.beans.TxReceiptContractBalanceChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSONObject;
import com.browser.protocol.EUDataGridResult;
import com.browser.tools.controller.BaseController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class TransactionController extends BaseController {

	@Autowired
	private TransactionService transactionService;
	@Autowired
	private TokenTransactionService tokenTransactionService;
	@Autowired
	private TxContractBalanceChangeService txContractBalanceChangeService;
	@Autowired
	private TxEventsService txEventsService;
	@Autowired
	private SwapTransactionService swapTransactionService;
	@Autowired
	private TokenService tokenService;
	@Autowired
	private AssetService assetService;
	
	private static Logger logger = LoggerFactory.getLogger(TransactionController.class);

	@ResponseBody
	@RequestMapping(value = "queryTransactionList", method = { RequestMethod.POST })
	public ResultMsg getTransactionList(@RequestBody BlTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		try {
			EUDataGridResult data = transactionService.getTransactionList(transaction);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}

	/**
	 * 返回合约代币列表
	 */
	@ResponseBody
	@RequestMapping(value = "listTokens", method = RequestMethod.POST)
	public ResultMsg listTokens(@RequestBody BlToken token) {
		ResultMsg resultMsg = new ResultMsg();
		try {
			EUDataGridResult data = tokenService.getActiveTokenList(token);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}

	// 根据条件返回token转账流水
	@ResponseBody
	@RequestMapping(value = "getTokenTransactionList", method = RequestMethod.POST)
	public ResultMsg getTokenTransactionList(@RequestBody BlTokenTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		try {
			EUDataGridResult data = tokenTransactionService.getTokenTransactionList(transaction);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}
	
	@ResponseBody
	@RequestMapping(value = "queryTransactionDetail", method = { RequestMethod.POST })
	public ResultMsg queryTransactionDetail(@RequestBody BlTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		if(null==transaction.getTrxId()||null==transaction.getOpType()) {
			resultMsg.setRetCode(ResultMsg.HTTP_CHECK_VALID);
			resultMsg.setRetMsg("参数不能为空");
			return resultMsg;
		}
		try {
			transaction.setOpType(null); // 改成不需要前端传正确的opType
			TransOpTypeRes data = transactionService.getOperationDetail(transaction);
			if(data != null) {
				// 查询events, contractBalanceChanges
				List<BlTxEvents> txEvents = txEventsService.selectAllByTrxId(transaction.getTrxId());
				data.setEvents(txEvents);
				List<BlTxContractBalanceChange> txContractBalanceChanges = txContractBalanceChangeService.selectAllByTrxId(transaction.getTrxId());
				txContractBalanceChanges = txContractBalanceChanges.stream().filter(
						x->!"contract_balances".equals(x.getChangeType())
				).collect(Collectors.toList()); // 排除contract_balances的changeType
				for(BlTxContractBalanceChange change : txContractBalanceChanges) {
					BlAsset changeAsset = assetService.selectByAssetId(change.getAssetId());
					if(changeAsset == null) {
						if(Constant.MAIN_ASSET_ID.equals(change.getAssetId())) {
							changeAsset = new BlAsset();
							changeAsset.setAssetId(Constant.MAIN_ASSET_ID);
							changeAsset.setSymbol(Constant.SYMBOL);
							changeAsset.setPrecision(Constant.PRECISION);
						} else {
							continue;
						}
					}
					change.setAssetSymbol(changeAsset.getSymbol());
					if(change.getAmount()!=null) {
						int assetDecimals = changeAsset.getPrecision().toString().length()-1;
						change.setAmountStr(new BigDecimal(change.getAmount()).setScale(assetDecimals, RoundingMode.FLOOR)
								.divide(PrecisionUtils.decimalsToPrecision(assetDecimals), RoundingMode.FLOOR).toString());
					}
				}
				data.setTxContractBalanceChanges(txContractBalanceChanges);
				// 找到这笔交易对应的token流水
				List<BlTokenTransaction> tokenTransactions = tokenTransactionService.selectAllByTrxId(transaction.getTrxId());
				data.setTokenTransactions(tokenTransactions);
				// 获取这笔交易对应的swap合约流水
				List<BlSwapTransaction> swapTransactions = swapTransactionService.selectAllByTrxId(transaction.getTrxId());
				data.setSwapTransactions(swapTransactions);
			}
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}
	
	@ResponseBody
	@RequestMapping(value = "queryTrxByAddr", method = { RequestMethod.POST })
	public ResultMsg queryTrxByAddr(@RequestBody BlTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		if(null==transaction.getAddress()) {
			resultMsg.setRetCode(ResultMsg.HTTP_CHECK_VALID);
			resultMsg.setRetMsg("参数不能为空");
			return resultMsg;
		}
		try {
			EUDataGridResult data = transactionService.selectMinerTrxList(transaction);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}

	@ResponseBody
	@RequestMapping(value = "queryTokenTrxByAddr", method = { RequestMethod.POST })
	public ResultMsg queryTokenTrxByAddr(@RequestBody BlTokenTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		if(null==transaction.getAddress()) {
			resultMsg.setRetCode(ResultMsg.HTTP_CHECK_VALID);
			resultMsg.setRetMsg("参数不能为空");
			return resultMsg;
		}
		try {
			EUDataGridResult data = tokenTransactionService.selectListByUserAddress(transaction);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}

	@ResponseBody
	@RequestMapping(value = "querySwapTrxByAddr", method = { RequestMethod.POST })
	public ResultMsg querySwapTrxByAddr(@RequestBody BlSwapTransaction transaction) {
		ResultMsg resultMsg = new ResultMsg();
		if(null==transaction.getAddress()) {
			resultMsg.setRetCode(ResultMsg.HTTP_CHECK_VALID);
			resultMsg.setRetMsg("参数不能为空");
			return resultMsg;
		}
		try {
			EUDataGridResult data = swapTransactionService.selectListByUserAddress(transaction);
			resultMsg.setRetCode(ResultMsg.HTTP_OK);
			resultMsg.setData(data);
		} catch (Exception e) {
			logger.error("系统错误", e);
			resultMsg.setRetCode(ResultMsg.HTTP_ERROR);
			resultMsg.setRetMsg(e.getMessage());
		}
		return resultMsg;
	}


}

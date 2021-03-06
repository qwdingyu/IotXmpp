package com.cqupt.xmpp.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import com.cqupt.xmpp.R;
import com.cqupt.xmpp.adapter.ChatWithNodeAdapter;
import com.cqupt.xmpp.bean.ChatMessage;
import com.cqupt.xmpp.bean.ChatSession;
import com.cqupt.xmpp.bean.NodeSubStatus;
import com.cqupt.xmpp.db.ChatMsgDao;
import com.cqupt.xmpp.db.ChatSesionDao;
import com.cqupt.xmpp.db.NodeStatusDao;
import com.cqupt.xmpp.fragment.ContactFragment;
import com.cqupt.xmpp.fragment.MessageFragment;
import com.cqupt.xmpp.manager.XmppConnectionManager;
import com.cqupt.xmpp.packet.GetNodeData;
import com.cqupt.xmpp.packet.SubscribNode;
import com.cqupt.xmpp.packet.UnsubNode;
import com.cqupt.xmpp.packet.WriteDataToNode;
import com.cqupt.xmpp.utils.ConstUtil;
import com.cqupt.xmpp.utils.DateUtils;
import com.cqupt.xmpp.utils.PreferencesUtils;
import com.cqupt.xmpp.utils.ToastUtils;
import com.cqupt.xmpp.widght.ChoiceListDialog;
import com.cqupt.xmpp.widght.DropdownListView;
import com.cqupt.xmpp.widght.SwipeBackActivity;
import com.cqupt.xmpp.widght.SwipeBackLayout;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.packet.IQ;

import java.util.ArrayList;

/**
 * Created by tiandawu on 2016/4/9.
 */
public class ChatWithNodeActivity extends SwipeBackActivity implements View.OnClickListener, DropdownListView.OnRefreshListenerHeader {

    private LinearLayout titlebarBackBtn, moreOption;
    private TextView title, subscribeNode, readData, settingNode;
    private DropdownListView dropdownListView;
    private ChatWithNodeAdapter adapter;
    private String groupName, childName, childJid, from, owner;
    private BroadcastReceiver receiver;
    private XmppConnectionManager manager = XmppConnectionManager.getXmppconnectionManager();
    private ChatMsgDao chatMsgDao;
    private ArrayList<ChatMessage> chatMessages;
    private int offset;
    private Roster mRoster;
    public static final String RECEIVED_MESSAGE = "received_message";
    public static final String SUBSCRIBE_SUCCESS = "subscribe_success";
    public static final String WRITE_SUCCESS = "write_success";
    public static final String UNSUBD_SUCCESS = "unsubed_success";
    private NodeStatusDao mNodeStatusDao;
    private ChatSesionDao mSesionDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_chat_with_node);
        getSwipeBackLayout().setEdgeTrackingEnabled(SwipeBackLayout.EDGE_ALL);
        initView();
        initData();
    }

    private void initView() {
        chatMsgDao = new ChatMsgDao(this);
        mSesionDao = new ChatSesionDao(this);
        mNodeStatusDao = new NodeStatusDao(this);
        chatMessages = new ArrayList<>();
        titlebarBackBtn = (LinearLayout) findViewById(R.id.titlebar_navigation);
        moreOption = (LinearLayout) findViewById(R.id.linear_menu_container);
        title = (TextView) findViewById(R.id.titlebar_navigation_title);
        subscribeNode = (TextView) findViewById(R.id.subscribe_node);
        readData = (TextView) findViewById(R.id.read_data);
        settingNode = (TextView) findViewById(R.id.setting_node);
        dropdownListView = (DropdownListView) findViewById(R.id.message_chat_listview);
        dropdownListView.setOnRefreshListenerHead(this);
    }

    private void initData() {


        groupName = getIntent().getStringExtra(ContactFragment.GROUP_NAME);
        childName = getIntent().getStringExtra(ContactFragment.CHILD_NAME);
        childJid = getIntent().getStringExtra(ContactFragment.CHILD_JID);
        from = childJid + "/" + groupName;
        owner = ConstUtil.getOwnerJid(this);
//        title.setText(childName);

        /**
         * 用于标记好友列表的报警标记
         * 保存被点击的是谁，便于清楚报警标记
         */
        PreferencesUtils.putSharePre(this, "clickedItemName", childName);


        /**
         * 用于标记消息列表的报警标记
         * 保存被点击的是谁，便于清楚报警标记
         */
        PreferencesUtils.putSharePre(this, "sessionItemName", childName);

//        Log.e("tt", "clickedItem = " + childName);

        if ("temprature1".equals(childName)) {
            title.setText("温度传感器1");
        } else if ("temprature2".equals(childName)) {
            title.setText("温度传感器2");
        } else if ("light1".equals(childName)) {
            title.setText("光照传感器1");
        } else if ("light2".equals(childName)) {
            title.setText("光照传感器2");
        } else if ("smoke1".equals(childName)) {
            title.setText("烟雾传感器1");
        } else if ("smoke2".equals(childName)) {
            title.setText("烟雾传感器2");
        }


        titlebarBackBtn.setOnClickListener(this);
        moreOption.setOnClickListener(this);
        subscribeNode.setOnClickListener(this);
        readData.setOnClickListener(this);
        settingNode.setOnClickListener(this);

        offset = 0;
        chatMessages = chatMsgDao.queryMsg(from, owner, offset);
        offset = chatMessages.size();
        adapter = new ChatWithNodeAdapter(this, chatMessages);
        dropdownListView.setAdapter(adapter);
        dropdownListView.setSelection(chatMessages.size());
    }

    @Override
    protected void onResume() {
        initBroadcastReceiver();
        super.onResume();
    }

    private void initBroadcastReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();
                if (action.equals(RECEIVED_MESSAGE)) {
                    ChatMessage chatMessage = chatMsgDao.queryTheLastMsg();

                    if (from.equals(chatMessage.getFrom())) {
                        chatMessages.add(chatMessages.size(), chatMessage);
                        adapter.notifyDataSetChanged();
                    }

                } else if (action.equals(SUBSCRIBE_SUCCESS)) {
                    String subType = intent.getStringExtra("subType");

                    if (subType.equals("period")) {
                        updateNodeStatus(subType);
                    } else if (subType.equals("highLimit")) {
                        updateNodeStatus(subType);
                    } else if (subType.equals("lowLimit")) {
                        updateNodeStatus(subType);
                    }

//                    ToastUtils.showShortToastInCenter(ChatWithNodeActivity.this, "订阅成功");
                    inserMyMessage("订阅成功", "false");
                    inserMySession("订阅成功");
                } else if (action.equals(WRITE_SUCCESS)) {
//                    ToastUtils.showLongToastInCenter(ChatWithNodeActivity.this, "写入成功");
                    inserMyMessage("设置成功", "false");
                    inserMySession("设置成功");
                } else if (action.equals(UNSUBD_SUCCESS)) {
                    NodeSubStatus nodeSubStatus = mNodeStatusDao.queryOneNodeInfo(from);
                    String subType = intent.getStringExtra("unsubType");
                    if (subType.equals("period")) {
                        nodeSubStatus.setPeriod("false");
                    } else if (subType.equals("highLimit")) {
                        nodeSubStatus.setHighLimit("false");
                    } else if (subType.equals("lowLimit")) {
                        nodeSubStatus.setLowLimit("false");
                    }
                    if (mNodeStatusDao.updateNodeInfo(nodeSubStatus) > 0) {
//                        ToastUtils.showShortToastInCenter(ChatWithNodeActivity.this, "取消订阅成功");
                        inserMyMessage("取消订阅成功", "false");
                        inserMySession("取消订阅成功");
                    }
                }

            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(RECEIVED_MESSAGE);
        filter.addAction(SUBSCRIBE_SUCCESS);
        filter.addAction(WRITE_SUCCESS);
        filter.addAction(UNSUBD_SUCCESS);
        registerReceiver(receiver, filter);
    }


    /**
     * 更改节点被订阅的状态
     *
     * @param subType
     */
    private void updateNodeStatus(String subType) {
        if (mNodeStatusDao.isExistTheNode(from)) {
            NodeSubStatus node = mNodeStatusDao.queryOneNodeInfo(from);

            if (subType.equals("period")) {
                node.setPeriod(subType);
            } else if (subType.equals("highLimit")) {
                node.setHighLimit(subType);
            } else if (subType.equals("lowLimit")) {
                node.setLowLimit(subType);
            }
            mNodeStatusDao.updateNodeInfo(node);
        } else {
            NodeSubStatus node = new NodeSubStatus();
            node.setNodeName(from);
            if (subType.equals("period")) {
                node.setPeriod(subType);
            } else if (subType.equals("highLimit")) {
                node.setHighLimit(subType);
            } else if (subType.equals("lowLimit")) {
                node.setLowLimit(subType);
            }
            mNodeStatusDao.insert(node);
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.titlebar_navigation:
                finish();
                break;
            case R.id.linear_menu_container:
                shoPopMoreOptions();
                break;
            case R.id.subscribe_node:
                if (!checkOnline()) {
                    ToastUtils.showLongToastInCenter(this, "当前结点不在线");
                    return;
                }
                doSubNode();
                break;
            case R.id.read_data:
                if (!checkOnline()) {
                    ToastUtils.showLongToastInCenter(this, "当前结点不在线");
                    return;
                }
                doReadNode();

                break;
            case R.id.setting_node:
                if (!checkOnline()) {
                    ToastUtils.showLongToastInCenter(this, "当前结点不在线");
                    return;
                }
                showWritePopWindow();
                break;
        }
    }

    /**
     * 显示右上角popMenu
     */
    private void shoPopMoreOptions() {
        final PopupWindow popWindow = new PopupWindow(ChatWithNodeActivity.this);
        View view = View.inflate(ChatWithNodeActivity.this, R.layout.pop_more_options, null);
        LinearLayout clearMessages = (LinearLayout) view.findViewById(R.id.clear_messages);
        popWindow.setContentView(view);
        popWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWindow.setOutsideTouchable(true);
        popWindow.setBackgroundDrawable(new ColorDrawable());
        clearMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatMsgDao chatMesgDao = new ChatMsgDao(ChatWithNodeActivity.this);
                if (chatMesgDao.deleteMsgByFrom(from) > 0) {
                    chatMessages.clear();
                    adapter.notifyDataSetChanged();
                    mSesionDao.deleteSessionByFrom(from);
                    ToastUtils.showShortToastInCenter(ChatWithNodeActivity.this, "操作成功");
                }

                popWindow.dismiss();
            }
        });

        popWindow.showAsDropDown(moreOption);
    }

    /**
     * 通过popWindow写入数据
     */
    private void showWritePopWindow() {

        final PopupWindow popWindow = new PopupWindow(ChatWithNodeActivity.this);
        View parent = View.inflate(ChatWithNodeActivity.this, R.layout.act_chat_with_node, null);
        final View view;
        if ("smoke".equals(groupName)) {
            view = View.inflate(ChatWithNodeActivity.this, R.layout.pop_write_data_to_smoke_node, null);
        } else {
            view = View.inflate(ChatWithNodeActivity.this, R.layout.pop_write_data_to_node, null);
        }

        popWindow.setContentView(view);
        popWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWindow.setBackgroundDrawable(new ColorDrawable());
        popWindow.setOutsideTouchable(true);
        popWindow.setFocusable(true);

        final EditText popInput = (EditText) view.findViewById(R.id.pop_editText);
        TextView cancleBtn = (TextView) view.findViewById(R.id.pop_cancle_button);
        TextView okButn = (TextView) view.findViewById(R.id.pop_ok_button);
        popWindow.showAtLocation(parent, Gravity.CENTER, 0, 0);

        cancleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popWindow.dismiss();
            }
        });

        okButn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = popInput.getText().toString().trim();
                WriteDataToNode packet = new WriteDataToNode();
                packet.setType(IQ.Type.GET);
                packet.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                packet.setTo(from);
                packet.setData(data);
                RadioButton samplePeriBtn = (RadioButton) view.findViewById(R.id.samplePeriBtn);
                RadioButton highLimitBtn = (RadioButton) view.findViewById(R.id.highLimitBtn);
                RadioButton lowLimitBtn = null;
                if (!"smoke".equals(groupName)) {
                    lowLimitBtn = (RadioButton) view.findViewById(R.id.lowLimitBtn);
                }

                if ("smoke".equals(groupName)) {
                    if (samplePeriBtn.isChecked()) {
                        packet.setWriteVar("samplePeri");
                        inserMyMessage("设置周期为：" + data + " 秒", "true");
                        inserMySession("设置周期为：" + data + " 秒");
                    } else if (highLimitBtn.isChecked()) {
                        packet.setWriteVar("highLimit");
                        inserMyMessage("设置上限为：" + data + " ppm", "true");
                        inserMySession("设置上限为：" + data + " ppm");
                    }
                } else if ("temprature".equals(groupName)){
                    if (samplePeriBtn.isChecked()) {
                        packet.setWriteVar("samplePeri");
                        inserMyMessage("设置周期为：" + data + " 秒", "true");
                        inserMySession("设置周期为：" + data + " 秒");
                    } else if (highLimitBtn.isChecked()) {
                        packet.setWriteVar("highLimit");
                        inserMyMessage("设置温度上限为：" + data + " ℃", "true");
                        inserMySession("设置温度上限为：" + data + " ℃");
                    } else if (lowLimitBtn.isChecked()) {
                        packet.setWriteVar("lowLimit");
                        inserMyMessage("设置温度下限为：" + data + " ℃", "true");
                        inserMySession("设置温度下限为：" + data + " ℃");
                    }
                } else if ("light".equals(groupName)) {
                    if (samplePeriBtn.isChecked()) {
                        packet.setWriteVar("samplePeri");
                        inserMyMessage("设置周期为：" + data + " 秒", "true");
                        inserMySession("设置周期为：" + data + " 秒");
                    } else if (highLimitBtn.isChecked()) {
                        packet.setWriteVar("highLimit");
                        inserMyMessage("设置光照强度上限为：" + data + " lx", "true");
                        inserMySession("设置光照强度上限为：" + data + " lx");
                    } else if (lowLimitBtn.isChecked()) {
                        packet.setWriteVar("lowLimit");
                        inserMyMessage("设置光照强度下限为：" + data + " lx", "true");
                        inserMySession("设置光照强度下限为：" + data + " lx");
                    }
                }


                manager.getXmppConnection().sendPacket(packet);
                popWindow.dismiss();
            }
        });

    }

    /**
     * 检查节点是否在线
     */
    private boolean checkOnline() {
        mRoster = XmppConnectionManager.getXmppconnectionManager().getRoster();
        RosterEntry entry = mRoster.getEntry(childJid);
        String type = mRoster.getPresence(entry.getUser()) + "";
        if (type.equals("available (online)")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 读取节点的数据
     */
    private void doReadNode() {

        ArrayList<String> list = new ArrayList<>();
        list.add("读周期");
        list.add("读上限");

        if (!"smoke".equals(groupName)) {
            list.add("读下限");
        }

        if ("temprature".equals(groupName)) {
//            list.add("读取湿度");
            list.add("读取当前温度");
        } else if ("smoke".equals(groupName)) {
            list.add("读取烟雾浓度");
        } else if ("light".equals(groupName)) {
            list.add("读取光照强度");
        }

        ChoiceListDialog dialog = new ChoiceListDialog(ChatWithNodeActivity.this, list);
        dialog.setOnChoiceListItemClickListener(new ChoiceListDialog.OnChoiceListItemClickListener() {
            @Override
            public void onListItemClick(int index) {

                if ("smoke".equals(groupName)) {
                    switch (index) {
                        case 0:
                            sendReadPacket("samplePeri");
                            inserMyMessage("获取当前订阅周期", "true");
                            inserMySession("获取当前订阅周期");
                            break;
                        case 1:
                            sendReadPacket("highLimit");
                            inserMyMessage("获取订阅上限", "true");
                            inserMySession("获取订阅上限");
                            break;
                        case 2:
                            sendReadPacket(groupName);
                            inserMyMessage("获取当前烟雾浓度", "true");
                            inserMySession("获取当前烟雾浓度");
                            break;
                    }
                } else if ("light".equals(groupName)) {

                    switch (index) {
                        case 0:
                            sendReadPacket("samplePeri");
                            inserMyMessage("获取当前订阅周期", "true");
                            inserMySession("获取当前订阅周期");
                            break;
                        case 1:
                            sendReadPacket("highLimit");
                            inserMyMessage("获取订阅上限", "true");
                            inserMySession("获取订阅上限");
                            break;
                        case 2:
                            sendReadPacket("lowLimit");
                            inserMyMessage("获取订阅下限", "true");
                            inserMySession("获取订阅下限");
                            break;
                        case 3:
                            sendReadPacket(groupName);
                            inserMyMessage("获取光照强度", "true");
                            inserMySession("获取当前温度");
                            break;
                    }
                } else if ("temprature".equals(groupName)) {
                    switch (index) {
                        case 0:
                            sendReadPacket("samplePeri");
                            inserMyMessage("获取当前订阅周期", "true");
                            inserMySession("获取当前订阅周期");
                            break;
                        case 1:
                            sendReadPacket("highLimit");
                            inserMyMessage("获取订阅上限", "true");
                            inserMySession("获取订阅上限");
                            break;
                        case 2:
                            sendReadPacket("lowLimit");
                            inserMyMessage("获取订阅下限", "true");
                            inserMySession("获取订阅下限");
                            break;
                        case 3:
                            sendReadPacket(groupName);
                            inserMyMessage("获取当前温度", "true");
                            inserMySession("获取当前温度");
                            break;
                    }
                }
            }
        });

        dialog.show();
    }

    /**
     * 发送读取数据的包
     */
    private void sendReadPacket(String type) {
        GetNodeData packet = new GetNodeData();
        packet.setType(IQ.Type.GET);
        packet.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
        packet.setTo(from);
        packet.setDataType(type);
        manager.getXmppConnection().sendPacket(packet);
    }

    /**
     * 订阅节点功能
     */
    private void doSubNode() {
        final NodeSubStatus nodeSubStatus = mNodeStatusDao.queryOneNodeInfo(from);

        final ArrayList<String> list = new ArrayList<>();

        if (nodeSubStatus.getPeriod().equals("false")) {
            list.add("周期订阅");
        } else {
            list.add("取消周期订阅");
        }

        if (nodeSubStatus.getHighLimit().equals("false")) {
            list.add("上限订阅");
        } else {
            list.add("取消上限订阅");
        }

        if (!"smoke".equals(groupName)) {
            if (nodeSubStatus.getLowLimit().equals("false")) {
                list.add("下限订阅");
            } else {
                list.add("取消下限订阅");
            }
        }


        final ChoiceListDialog dialog = new ChoiceListDialog(this, list);
        dialog.setOnChoiceListItemClickListener(new ChoiceListDialog.OnChoiceListItemClickListener() {
            @Override
            public void onListItemClick(int index) {
                switch (index) {
                    /**
                     * 周期订阅
                     */
                    case 0:
                        if (list.get(0).equals("周期订阅")) {
                            SubscribNode packetPeriod = new SubscribNode(from, "period", groupName);
                            packetPeriod.setType(IQ.Type.SET);
                            packetPeriod.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(packetPeriod);

                            inserMyMessage("周期性订阅", "true");
                            inserMySession("周期性订阅");
                        } else {
                            UnsubNode unsubNodePacket = new UnsubNode(from, groupName, "period");
                            unsubNodePacket.setType(IQ.Type.SET);
                            unsubNodePacket.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(unsubNodePacket);

                            inserMyMessage("取消周期性订阅", "true");
                            inserMySession("取消周期性订阅");
                        }

                        break;
                    /**
                     * 上线订阅
                     */
                    case 1:
                        if (list.get(1).equals("上限订阅")) {
                            SubscribNode packetHightLimit = new SubscribNode(from, "highLimit", groupName);
                            packetHightLimit.setType(IQ.Type.SET);
                            packetHightLimit.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(packetHightLimit);
                            inserMyMessage("订阅上限警报", "true");
                            inserMySession("订阅上限警报");
                        } else {
                            UnsubNode unsubNodePacket = new UnsubNode(from, groupName, "highLimit");
                            unsubNodePacket.setType(IQ.Type.SET);
                            unsubNodePacket.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(unsubNodePacket);
                            inserMyMessage("取消上限警报", "true");
                            inserMySession("取消上限警报");
                        }
                        break;
                    /**
                     * 下线订阅
                     */
                    case 2:
                        if (list.get(2).equals("下限订阅")) {
                            SubscribNode packetLowLimit = new SubscribNode(from, "lowLimit", groupName);
                            packetLowLimit.setType(IQ.Type.SET);
                            packetLowLimit.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(packetLowLimit);
                            inserMyMessage("订阅下限警报", "true");
                            inserMySession("订阅下限警报");
                        } else {
                            UnsubNode unsubNodePacket = new UnsubNode(from, groupName, "lowLimit");
                            unsubNodePacket.setType(IQ.Type.SET);
                            unsubNodePacket.setFrom(ConstUtil.getOwnerJid(ChatWithNodeActivity.this));
                            manager.getXmppConnection().sendPacket(unsubNodePacket);
                            inserMyMessage("取消下限警报", "true");
                            inserMySession("取消下限警报");
                        }
                        break;
                }
            }
        });
        dialog.show();
    }

    /**
     * 下拉加载更多
     */
    @Override
    public void onRefresh() {
        ArrayList<ChatMessage> list = chatMsgDao.queryMsg(from, owner, offset);
        if (list.size() <= 0) {
            dropdownListView.setSelection(0);
            dropdownListView.onRefreshCompleteHeader();
            return;
        }
        chatMessages.addAll(0, list);
        offset = chatMessages.size();
        dropdownListView.onRefreshCompleteHeader();
        adapter.notifyDataSetChanged();
        dropdownListView.setSelection(list.size());
    }

    @Override
    protected void onPause() {


        Intent clearAlarmIntent = new Intent();
        clearAlarmIntent.setAction(ContactFragment.FRIENDS_STATUS_CHANGED);
        sendBroadcast(clearAlarmIntent);

        Intent intent = new Intent();
        intent.setAction(MessageFragment.RECEIVED_NEW_SESSION);
        sendBroadcast(intent);

        unregisterReceiver(receiver);
        super.onPause();
    }


    /**
     * 插入到消息列表
     *
     * @param message
     * @param flag
     */
    private void inserMyMessage(String message, String flag) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setBody(message);
        chatMessage.setFlag(flag);
        chatMessage.setFrom(from);
        chatMessage.setOwner(owner);
        chatMessage.setTo(owner);
        chatMessage.setTime(DateUtils.getNowDateTime());
        chatMessages.add(chatMessage);
        adapter.notifyDataSetChanged();
        chatMsgDao.insert(chatMessage);
    }


    /**
     * 插入到会话列表
     *
     * @param message
     */
    private void inserMySession(String message) {
        ChatSession chatSession = new ChatSession();
        chatSession.setBody(message);
        chatSession.setFrom(from);
        chatSession.setOwner(owner);
        chatSession.setTo(owner);
        chatSession.setTime(DateUtils.getNowDateTime());

        if (mSesionDao.isExistTheSession(from, owner)) {
            mSesionDao.updateSession(chatSession);
        } else {
            mSesionDao.insert(chatSession);
        }
    }

}

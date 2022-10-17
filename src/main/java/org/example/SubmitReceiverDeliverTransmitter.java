package org.example;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.*;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class SubmitReceiverDeliverTransmitter {
    static Logger log;
    static {
        try(FileInputStream ins = new FileInputStream("C:\\Users\\d.caraja\\IdeaProjects\\firstPart\\src\\main\\java\\org\\example\\log.config")){
            LogManager.getLogManager().readConfiguration(ins);
            log = Logger.getLogger(SubmitReceiverDeliverTransmitter.class.getName());
            log.addHandler(new FileHandler("D:\\smppLogs\\server_logs.txt"));
        }catch (Exception ignore){
            ignore.printStackTrace();
        }
    }
    public static SimpleDateFormat formatForDateNow = new SimpleDateFormat("yyyy_dd_MM_hh_mm_ss");

    public static void main(String[] args) {
        try {
            ServerMessageReceiverListener serverMessageReceiverListener = new ServerMessageReceiverListener();
            log.info("Слушаем...");
            SMPPServerSessionListener sessionListener = new SMPPServerSessionListener(8080);
            sessionListener.setMessageReceiverListener(serverMessageReceiverListener);
            SMPPServerSession session = sessionListener.accept();
            log.info("Подключился клиент");
            try {
                BindRequest request = session.waitForBind(30000);
                log.info("Получен бинд на подключение от клиента " + request.getSystemId() + ", его пароль " + request.getPassword());
                if ("smpp_user".equals(request.getSystemId()) &&
                        "d1234".equals(request.getPassword())) {

                    // accepting request and send bind response immediately
                    log.info("Разрешить подключение");
                    request.accept("Успешно!");
                } else {
                    log.info("Запретить подключение " + request.getSystemId());
                    request.reject(SMPPConstant.STAT_ESME_RINVPASWD);
                }
            }catch(TimeoutException | PDUStringException e){
                log.info("Ошибка: " + e);
                session.unbindAndClose();
                sessionListener.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class ServerMessageReceiverListener implements org.jsmpp.session.ServerMessageReceiverListener{
    final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();
    DataBaseConnect dbfw = new DataBaseConnect();
    private static final Logger log = Logger.getLogger(String.valueOf(SubmitReceiverDeliverTransmitter.class));
    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession source) {
        String SMS = new String(submitSm.getShortMessage());
        MessageId messageID;
        System.out.println("Полученное сообщение: " + SMS);
        try {
            dbfw.insertInDB("sms_server", SMS, SubmitReceiverDeliverTransmitter.formatForDateNow.format(new Date().getTime()));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if(SMS.equals("stop")) System.exit(0);
        // need message_id to response submit_sm, optional parameters add in SMPP 5.0
        try {
            source.deliverShortMessage(submitSm.getServiceType(),
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, submitSm.getDestAddress(),
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, submitSm.getSourceAddr(),
                    new ESMClass(1), (byte) 1, (byte) 1, new RegisteredDelivery(), DataCodings.ZERO,
                    "DELIVERED".getBytes());
            messageID = messageIDGenerator.newMessageId();
            log.info("ID сообщения <<" + SMS + ">>: " + messageID);
        } catch (PDUException | ResponseTimeoutException | InvalidResponseException |
                 NegativeResponseException | IOException e) {
            throw new RuntimeException(e);
        }
        return new SubmitSmResult(messageID, new OptionalParameter[0]);
    }

    @Override
    public QuerySmResult onAcceptQuerySm(QuerySm querySm,
                                         SMPPServerSession source) {
        return null;
    }

    @Override
    public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public SubmitMultiResult onAcceptSubmitMulti(
            SubmitMulti submitMulti, SMPPServerSession source) {
        return new SubmitMultiResult(messageIDGenerator.newMessageId().getValue(), new UnsuccessDelivery[]{}, new OptionalParameter[]{});
    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) {
        return new DataSmResult(messageIDGenerator.newMessageId(), new OptionalParameter[]{});
    }

    @Override
    public BroadcastSmResult onAcceptBroadcastSm(final BroadcastSm broadcastSm, final SMPPServerSession source) {
        return new BroadcastSmResult(messageIDGenerator.newMessageId(), new OptionalParameter[]{});
    }

    @Override
    public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public QueryBroadcastSmResult onAcceptQueryBroadcastSm(final QueryBroadcastSm queryBroadcastSm,
                                                           final SMPPServerSession source) {
        return null;
    }
}
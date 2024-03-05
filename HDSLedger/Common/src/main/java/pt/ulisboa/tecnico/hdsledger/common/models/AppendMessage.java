package pt.ulisboa.tecnico.hdsledger.common.models;

public class AppendMessage {
    
    private String messageId;

    private String messageSenderId;

    private String content;

    public AppendMessage(String messageId, String messageSenderId, String content) {
        this.messageId = messageId;
        this.messageSenderId = messageSenderId;
        this.content = content;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessageSenderId() {
        return messageSenderId;
    }

    public String getContent() {
        return content;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setMessageSenderId(String messageSenderId) {
        this.messageSenderId = messageSenderId;
    }

    public void setContent(String content) {
        this.content = content;
    }

}

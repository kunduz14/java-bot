package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

public class TelegramBot extends TelegramLongPollingBot {
    // enum
    private static final String ADD_EXPENSE_BTN = "Добавить трату";
    private static final String SHOW_CATEGORIES_BTN = "Категории";
    private static final String SHOW_EXPENSES_BTN = "Показать все траты";

    private static final String IDLE_STATE = "IDLE";
    private static final String AWAITS_CATEGORY_STATE = "AWAITS CATEGORY";
    private static final String AWAITS_EXPENSE_STATE = "AWAITS EXPENSE";

    private static final Map<Long, ChatState> CHATS = new HashMap<>();

    static List<String> INITIAL_KEYBOARD = List.of(
            ADD_EXPENSE_BTN,
            SHOW_CATEGORIES_BTN,
            SHOW_EXPENSES_BTN
    );

    @Override
    public String getBotUsername() {
        return "My expenses";
    }

    @Override
    public String getBotToken() {
        return System.getenv("TG_BOT_TOKEN");
    }

    @Override
    // Вызывается каждый раз, когда бот получает сообщения
    public void onUpdateReceived(Update update) {
        // Проверка есть ли текст в сообщении
        if (update.hasMessage() && update.getMessage().hasText()) {

            Message message = update.getMessage(); // Сообщение, которое было отправлено
            Long chatId = message.getChatId();
            CHATS.putIfAbsent(chatId, new ChatState(IDLE_STATE));

            User from = message.getFrom(); // Кем отправлено
            String text = message.getText(); // Что отправлено
            String logMessage = from.getUserName() + ": " + text;
            System.out.println(logMessage);

            ChatState currentChat = CHATS.get(chatId);
            switch (currentChat.state) {
                case IDLE_STATE -> handleIdle(message, currentChat);
                case AWAITS_CATEGORY_STATE -> handleAwaitsCategory(message, currentChat);
                case AWAITS_EXPENSE_STATE -> handleAwaitsExpense(message, currentChat);
            }
        } else {
            System.out.println("Unsupported update");
        }
    }

    private ReplyKeyboard buildKeyboard(List<String> buttonNames) {
        if (buttonNames == null || buttonNames.isEmpty()) return new ReplyKeyboardRemove(true);

        List<KeyboardRow> rows = new ArrayList<>();
        for (String buttonName: buttonNames) {
            KeyboardRow row = new KeyboardRow();
            row.add(buttonName);
            rows.add(row);
        }
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private void handleIdle(Message incomingMessage, ChatState currentChat) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();

        switch (incomingText) {
            case "/start" -> changeState(
                    IDLE_STATE,
                    chatId,
                    currentChat,
                    "Hi there!",
                    INITIAL_KEYBOARD
            );
            case SHOW_CATEGORIES_BTN -> changeState(
                    IDLE_STATE,
                    chatId,
                    currentChat,
                    currentChat.getFormattedCategories(),
                    INITIAL_KEYBOARD
            );
            case SHOW_EXPENSES_BTN -> changeState(
                    IDLE_STATE,
                    chatId,
                    currentChat,
                    currentChat.getFormattedExpenses(),
                    INITIAL_KEYBOARD
            );
            case ADD_EXPENSE_BTN -> changeState(
                    AWAITS_CATEGORY_STATE,
                    chatId,
                    currentChat,
                    "Укажите категорию",
                    null
            );
            default -> changeState(
                    IDLE_STATE,
                    chatId,
                    currentChat,
                    "Я не знаю такой команды",
                    null
            );
        }
    }

    private void handleAwaitsCategory(Message incomingMessage, ChatState currentChat) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();
        currentChat.expenses.putIfAbsent(incomingText, new ArrayList<>());
        currentChat.date = incomingText;
        changeState(
                AWAITS_EXPENSE_STATE,
                chatId,
                currentChat,
                "Введите сумму",
                null
        );
    }

    private void handleAwaitsExpense(Message incomingMessage, ChatState currentChat) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();
        Integer expense = Integer.parseInt(incomingText);

        if (currentChat.date == null) {
            changeState(
                    IDLE_STATE,
                    chatId,
                    currentChat,
                    "Что-то пошло не так",
                    List.of(ADD_EXPENSE_BTN, SHOW_CATEGORIES_BTN, SHOW_EXPENSES_BTN)
            );
            return;
        }

        currentChat.expenses.get(currentChat.date).add(expense);
        changeState(
                IDLE_STATE,
                chatId,
                currentChat,
                "Трата успешно добавлена",
                List.of(ADD_EXPENSE_BTN, SHOW_CATEGORIES_BTN, SHOW_EXPENSES_BTN)
        );
    }

    private void changeState(String newState,
                             Long chatId,
                             ChatState currentChat,
                             String messageText,
                             List<String> buttonNames) {
        System.out.println(currentChat.state + " -> " + newState);
        currentChat.state = newState;

        ReplyKeyboard keyboard = buildKeyboard(buttonNames);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("ERROR");
            System.out.println("e");
        }
    }
}

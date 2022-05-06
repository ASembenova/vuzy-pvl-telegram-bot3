package kz.saa.vuzypvltelegrambot.telegram.handler;


import com.vdurmont.emoji.EmojiParser;
import kz.saa.vuzypvltelegrambot.db.domain.User;
import kz.saa.vuzypvltelegrambot.model.Step;
import kz.saa.vuzypvltelegrambot.service.*;
import kz.saa.vuzypvltelegrambot.service.memory.LocaleService;
import kz.saa.vuzypvltelegrambot.service.memory.UserServiceDBImpl;
import kz.saa.vuzypvltelegrambot.service.report.PassportService;
import kz.saa.vuzypvltelegrambot.service.report.CompareService;
import kz.saa.vuzypvltelegrambot.service.speciality.InfoService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
public class MessageHandler {
    //private final UserCash userCash;
    private final LocaleService localeService;
    private final MessageSender messageSender;
    private final MenuService menuService;
    private final InfoService infoService;
    private final CompareService compareService;
    private final PassportService passportService;
    private final UserServiceDBImpl userService;
    private final ParametersMenuService parametersMenuService;


    public MessageHandler(/*UserCash userCash, */LocaleService localeService, MessageSender messageSender, MenuService menuService, InfoService infoService, CompareService compareService, PassportService passportService, UserServiceDBImpl userService, ParametersMenuService parametersMenuService) {
        /*this.userCash = userCash;*/
        this.localeService = localeService;
        this.messageSender = messageSender;
        this.menuService = menuService;
        this.infoService = infoService;
        this.compareService = compareService;
        this.passportService = passportService;
        this.userService = userService;
        this.parametersMenuService = parametersMenuService;
    }

    public BotApiMethod<?> handle(Message message) {
        String messageText = message.getText();
        long chatId = message.getChatId();
        String lang = localeService.getLocaleTag(chatId);
        Step newStep = null;
        Step currentStep = null;
        if(!userService.containsUser(chatId) || messageText.equals("/start")){
            userService.addUser(new User(chatId, message.getFrom().getFirstName(), message.getFrom().getLastName()));
            currentStep = userService.getLastStep(chatId).getStep();
            newStep = nextStep(currentStep, messageText, chatId, lang);
        } else{
            if(!messageText.equals(localeService.getMessageByLang("menu.back", lang))) {
                currentStep = userService.getLastStep(chatId).getStep();
                if(messageText.equals(localeService.getMessageByLang("menu.help", lang))){
                    newStep = Step.HELP;
                } else{
                    newStep = nextStep(currentStep, messageText, chatId, lang);
                }
            } else {
                currentStep = userService.deleteLastStep(chatId).getStep();
                newStep = userService.getLastStep(chatId).getStep();
            }
        }
        //System.out.println("current: "+currentStep+", new: "+newStep);
        userService.addStep(chatId, newStep);
        return processNewStep(newStep, chatId, message, lang);
    }

    private BotApiMethod<?> processNewStep(Step step, long chatId, Message message, String lang) {
        String messageText = message.getText();
        String stepName = step.name();
        switch (stepName) {
            case "WELCOME":
                return menuService.getWelcomeMsg(chatId);
            case "MAIN_MENU":
                return menuService.getMainMenuMsg(chatId);
            case "SELECT_ALL":
                return menuService.getSelectAllMsg(chatId);
            case "SELECT_ONE":
                if (messageText.equals(localeService.getMessageByLang("menu.one", lang)) ||
                        messageText.equals(localeService.getMessageByLang("menu.back", lang))) {
                    return menuService.getSelectOneMsg(chatId, lang);
                } else {
                    return infoService.getOneVuzInfo(chatId, messageText);
                }
            case "LANG_MENU":
                if (messageText.equals(localeService.getMessageByLang("menu.change_lang", lang))) {
                    return menuService.getSelectLangMsg(chatId);
                }
                if (messageText.equals(EmojiParser.parseToUnicode(localeService.getMessageByLang("lang.kz", lang)))) {
                    localeService.changeLang("kz", chatId);
                } else if (messageText.equals(EmojiParser.parseToUnicode(localeService.getMessageByLang("lang.ru", lang)))) {
                    localeService.changeLang("ru", chatId);
                }
                userService.deleteLastStep(chatId);
                return menuService.getMainMenuMsg(chatId);
            case "PARAMS_ALL":
                if (messageText.equals(localeService.getMessageByLang("menu_all.all_param", lang))) {
                    return menuService.getAllParamsMsg(chatId, lang);
                } else if (messageText.equals(EmojiParser.parseToUnicode(localeService.getMessageByLang("btn.generate_pdf", lang)))) {
                    passportService.getAllParamsPassport(chatId, message.getFrom().getFirstName(), message.getFrom().getLastName());
                }
                return null;
            case "PARAMS_CUSTOM":
                return parametersMenuService.getChooseParamsMsg(chatId, lang);
/*else if(messageText.equals(localeService.getMessage("menu_all.all_param", chatId))) {
                    return menuService.getAllParamsMsg(chatId);
                } else if(messageText.equals(localeService.getMessage("menu.search", chatId))){
                    return menuService.getSearchMsg(chatId);
                }*/
            case "COMPARE_SPEC":
                return menuService.getCompareSpecMsg(chatId);
            case "COMPARE_BYNAME":
            case "COMPARE_BYCODE":
            case "COMPARE_BYNAME_AND_CODE":
                return compareService.getCompareMessage(chatId, stepName.toLowerCase());
            case "SEARCH":
                if (messageText.equals(localeService.getMessageByLang("menu.search", lang))) {
                    return menuService.getSearchMsg(chatId);
                } else {
                    return menuService.getSearchResults(chatId, messageText);
                }
            case "HELP":
                if (messageText.equals(localeService.getMessageByLang("menu.help", lang))) {
                    return menuService.getHelpMessage(chatId);
                } else if (messageText.equals(localeService.getMessageByLang("help.dataset_info", lang))) {
                    return menuService.getHelpDatasetPassportMessage(chatId);
                } else if (messageText.equals(localeService.getMessageByLang("help.opendata", lang))) {
                    return menuService.getHelpOpenDataMessage(chatId, lang);
                } else if (messageText.equals(localeService.getMessageByLang("help.dataegov", lang))) {
                    return menuService.getHelpDataEgovMessage(chatId, lang);
                } else if (messageText.equals(localeService.getMessageByLang("help.write", lang))) {
                    return menuService.getHelpWriteMessage(chatId, lang);
                } else messageSender.sendMsgToDeveloper(message, lang);
        }
        return null;
    }

    private Step nextStep(Step currentStep, String messageText, long chatId, String lang) {
        String name = currentStep.name();
        switch (name){
            case "START":
                return Step.WELCOME;
            /*case "SELECT_LANG":*/
            case "WELCOME":
                if(messageText.equals(EmojiParser.parseToUnicode(localeService.getMessageByLang("lang.kz", lang)))){
                    localeService.changeLang("kz", chatId);
                } else {
                    localeService.changeLang("ru", chatId);
                }
                return Step.MAIN_MENU;
            case "MAIN_MENU":
                if(messageText.equals(localeService.getMessageByLang("menu.all", lang))){
                    return Step.SELECT_ALL;
                } else if(messageText.equals(localeService.getMessageByLang("menu.one", lang))){
                    return Step.SELECT_ONE;
                } else if(messageText.equals(localeService.getMessageByLang("menu.change_lang", lang))){
                    return Step.LANG_MENU;
                } else {
                    return Step.MAIN_MENU;
                }
            case "PARAMS_CUSTOM":
            case "SELECT_ALL":
                if(messageText.equals(localeService.getMessageByLang("menu_all.all_param", lang))){
                    return Step.PARAMS_ALL;
                } else if(messageText.equals(localeService.getMessageByLang("menu_all.select_param", lang))){
                    return Step.PARAMS_CUSTOM;
                } else if(messageText.equals(localeService.getMessageByLang("menu_all.compare_number", lang))){
                    return Step.COMPARE_NUMBER;
                } else if(messageText.equals(localeService.getMessageByLang("menu_all.compare_spec", lang))){
                    return Step.COMPARE_SPEC;
                } else if(messageText.equals(localeService.getMessageByLang("menu.search", lang))){
                    return Step.SEARCH;
                } else {
                    return Step.SELECT_ALL;
                }
            case "COMPARE_SPEC":
            case "COMPARE_BYNAME":
            case "COMPARE_BYCODE":
            case "COMPARE_BYNAME_AND_CODE":
                if(messageText.equals(localeService.getMessageByLang("menu.compare_byname", lang))){
                    return Step.COMPARE_BYNAME;
                } else if(messageText.equals(localeService.getMessageByLang("menu.compare_bycode", lang))){
                    return Step.COMPARE_BYCODE;
                } else if(messageText.equals(localeService.getMessageByLang("menu.compare_byname_and_code", lang))){
                    return Step.COMPARE_BYNAME_AND_CODE;
                } else {
                    return Step.SELECT_ALL;
                }
        }
        return currentStep;
    }


}

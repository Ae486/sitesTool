/**
 * i18n (Internationalization) Configuration
 *
 * To use i18n, you need to install the following dependencies:
 * pnpm add i18next react-i18next i18next-browser-languagedetector
 *
 * After installing, uncomment the code below and wrap your App with I18nextProvider.
 */
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import zhCN from "./locales/zh-CN.json";
import enUS from "./locales/en-US.json";
const resources = {
    "zh-CN": { translation: zhCN },
    "en-US": { translation: enUS },
};
i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
    resources,
    fallbackLng: "zh-CN",
    defaultNS: "translation",
    interpolation: {
        escapeValue: false, // React already handles XSS
    },
    detection: {
        order: ["localStorage", "navigator", "htmlTag"],
        caches: ["localStorage"],
        lookupLocalStorage: "nav-checkin-language",
    },
});
export default i18n;
/**
 * Usage in components:
 *
 * import { useTranslation } from "react-i18next";
 *
 * const MyComponent = () => {
 *   const { t } = useTranslation();
 *   return <h1>{t("common.save")}</h1>;
 * };
 *
 * Change language:
 * import i18n from "../i18n";
 * i18n.changeLanguage("en-US");
 */

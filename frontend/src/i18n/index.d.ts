/**
 * i18n (Internationalization) Configuration
 *
 * To use i18n, you need to install the following dependencies:
 * pnpm add i18next react-i18next i18next-browser-languagedetector
 *
 * After installing, uncomment the code below and wrap your App with I18nextProvider.
 */
import i18n from "i18next";
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

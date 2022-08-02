import { NativeModules } from 'react-native';

import { isAndroid } from '../../utils';

const SearchController = NativeModules.MGLMapboxSearchController;

class SearchManager {
  constructor() {}

  /**
   * @param {string} queryText
   * @param {string | undefined} suggestionID
   * @param {Array<number> | undefined} origin
   * @return {any}
   */
  async forwardSearch(queryText, suggestionID, origin) {
    const result = await SearchController.forwardSearch(
      queryText,
      suggestionID,
      origin,
    );
    return result;
  }

  /**
   * @param {string} queryText
   * @param {Array<number>} origin
   * @return {Promise<any>}
   */
  async retrieveSuggestions(queryText, origin) {
    return SearchController.retrieveSuggestions(queryText, origin);
  }

  stopSearch() {
    if (isAndroid()) {
      SearchController.stopSearch();
    }
  }
}

const searchManager = new SearchManager();
export default searchManager;

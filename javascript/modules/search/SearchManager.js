import { isAndroid } from 'javascript/utils';
import { NativeModules } from 'react-native';

const SearchController = NativeModules.MGLMapboxSearchController;

class SearchManager {
  constructor() {}

  /**
   * @param {string} queryText
   * @return {any}
   */
  async forwardSearch(queryText) {
    const result = await SearchController.forwardSearch(queryText);
    return result;
  }

  stopSearch(queryText) {
    if (isAndroid) {
      SearchController.stopSearch();
    }
  }
}

const searchManager = new SearchManager();
export default searchManager;

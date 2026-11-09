import { isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const src = fileURLToPath(new URL('./src/', import.meta.url));

function area(file) {
  const path = relative(src, file.split('?')[0]);
  if (isAbsolute(path) || path === '..' || path.startsWith('..' + sep)) return null;
  const [folder, feature] = path.split(sep);
  if (folder === 'features' && feature) return 'features/' + feature;
  if (folder === 'shared') return 'shared';
  if (folder === 'app' || folder === 'main.jsx' || folder === 'styles.css') return 'app';
  return 'unclassified';
}

export function deliveryImportBoundaries() {
  return {
    name: 'delivery-import-boundaries',
    enforce: 'pre',
    async resolveId(source, importer, options) {
      if (!importer || !isAbsolute(importer)) return null;
      const from = area(importer);
      if (!from) return null;
      const target = await this.resolve(source, importer, { ...options, skipSelf: true });
      if (!target || target.external || !isAbsolute(target.id)) return target;
      const to = area(resolve(target.id));
      if (!to) {
        if (resolve(target.id).split(sep).includes('node_modules')) return target;
        this.error(`Delivery import boundary: ${from} cannot import source outside src (${source}).`);
      }

      // App composes features. Features share utilities and keep their own code private.
      if (from === 'unclassified' || to === 'unclassified' ||
          (from !== 'app' && to !== from && to !== 'shared')) {
        this.error(`Delivery import boundary: ${from} cannot import ${to} (${source}).`);
      }
      return target;
    },
  };
}

import React from 'react';
import { BRANDS, BrandSwitch, useBrand } from './brand.jsx';
import { PRODUCTS, initials } from './app/config.js';
import { ThemeSwitch, themeMark, useTheme } from './app/theme.jsx';
import { Menu, MenuSection } from './ui/index.jsx';

/** The brand lockup: the product name with "by Deepwater" under it, or just the parent brand. */
export function Brand({ product }) {
  const [theme] = useTheme();
  const [brand] = useBrand();
  const mark = brand === 'nibble' ? '/brand/nibble.svg' : themeMark(theme);
  const parent = BRANDS[brand].name;
  return <span className="brand"><img src={mark} alt="" width="40" height="40" />
    <span className="brand-text"><b>{product || parent}</b>{product && <small>by {parent}</small>}</span></span>;
}

/** Product choice: Echo, Harbor, Amber. One quiet segmented control. */
export function ProductSwitch({ value, onChange }) {
  return <nav className="product-switch" aria-label="Products">
    {PRODUCTS.map(option => <button key={option.id} type="button" aria-pressed={value === option.id} onClick={() => onChange(option.id)}>
      <b>{option.name}</b><small>{option.hint}</small></button>)}
  </nav>;
}

/**
 * The account corner. Appearance, brand and sign-out live in one menu behind the avatar, so the
 * header keeps three things: where you are, which product, and you. `children` go before the menu
 * (for example an "Ask AI" button).
 */
export function AccountMenu({ user, profileOpen, onProfile, onSignOut, children }) {
  return <div className="account">
    {children}
    <Menu label={`Account and settings for ${user.name}`} className="account-menu"
      summary={<span className={`account-chip${profileOpen ? ' active' : ''}`}>
        <span className="account-avatar" aria-hidden="true">{initials(user.name)}</span>
        <span className="account-label">{user.name}</span>
        <span className="account-caret" aria-hidden="true" /></span>}>
      <MenuSection>
        <b className="ui-menu-name">{user.name}</b>
        <span className="ui-menu-email">{user.email}</span>
      </MenuSection>
      <button type="button" className="ui-menu-item" data-menu-close aria-current={profileOpen ? 'page' : undefined} onClick={onProfile}>Your profile</button>
      <MenuSection title="Appearance"><ThemeSwitch /></MenuSection>
      <MenuSection title="Brand"><BrandSwitch /></MenuSection>
      <button type="button" className="ui-menu-item" data-menu-close onClick={onSignOut}>Sign out</button>
    </Menu>
  </div>;
}

/** The signed-in header. */
export default function AppHeader({ product, onProduct, productLabel, user, profileOpen, onProfile, onSignOut, accountExtras }) {
  return <header className="site-header">
    <Brand product={productLabel} />
    <ProductSwitch value={product} onChange={onProduct} />
    <AccountMenu user={user} profileOpen={profileOpen} onProfile={onProfile} onSignOut={onSignOut}>{accountExtras}</AccountMenu>
  </header>;
}

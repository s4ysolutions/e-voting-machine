import * as React from 'react';
import {Grid2 as Grid, styled} from '@mui/material';
import Item from './Item';
import VotingOwner from '../panels/VotingOwner';

const size = {xs: 12, sm: 6};

const ITEM_SPACING = 2;

// TODO: 16px should be a theme value
const HeightGridItem = styled(Grid)(({theme}) => ({
  height: `calc(100vh - ${theme.spacing(ITEM_SPACING)})`,
  [theme.breakpoints.up('sm')]: {
    height: `calc(50vh - ${theme.spacing(ITEM_SPACING)})`,
  },
}));

const Desktop = (): React.ReactElement =>
  <Grid container spacing={2}>
    <HeightGridItem size={size}>
      <Item><VotingOwner/></Item>
    </HeightGridItem>
    <HeightGridItem size={size}>
      <Item>2</Item>
    </HeightGridItem>
    <HeightGridItem size={size}>
      <Item>3</Item>
    </HeightGridItem>
    <HeightGridItem size={size}>
      <Item>4</Item>
    </HeightGridItem>
  </Grid>;

export default Desktop;

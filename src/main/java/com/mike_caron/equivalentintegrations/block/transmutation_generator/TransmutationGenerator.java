package com.mike_caron.equivalentintegrations.block.transmutation_generator;

import com.mike_caron.equivalentintegrations.EquivalentIntegrationsMod;
import com.mike_caron.equivalentintegrations.block.BlockBase;
import com.mike_caron.equivalentintegrations.block.TransmutationBlockBase;
import com.mike_caron.equivalentintegrations.block.transmutation_chamber.TransmutationChamberTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TransmutationGenerator
        extends TransmutationBlockBase
{
    public static final String id = "transmutation_generator";

    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    public static final PropertyBool GENERATING = PropertyBool.create("generating");

    public static final int GUI_ID = 2;
    public TransmutationGenerator()
    {
        super(id);

        setDefaultState(
                this.blockState.getBaseState()
                .withProperty(ACTIVE, false)
                .withProperty(GENERATING, false)
        );
    }

    @Nonnull
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TransmutationGeneratorTileEntity();
    }

    @SuppressWarnings("deprecation")
    @Override
    @Deprecated
    @Nonnull
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos)
    {
        TransmutationGeneratorTileEntity tileEntity = getTE(worldIn, pos);
        if(tileEntity == null) return getDefaultState();

        return state
                .withProperty(ACTIVE, tileEntity.hasOwner())
                .withProperty(GENERATING, tileEntity.isGenerating());
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, ACTIVE, GENERATING);
    }

    @Nullable
    private TransmutationGeneratorTileEntity getTE(IBlockAccess worldIn, BlockPos pos)
    {
        TileEntity ret = worldIn.getTileEntity(pos);
        if(ret instanceof TransmutationGeneratorTileEntity) return (TransmutationGeneratorTileEntity)ret;
        return null;
    }



    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if(worldIn.isRemote) return true;

        TransmutationGeneratorTileEntity te = getTE(worldIn, pos);

        if(te == null) return false;

        playerIn.openGui(EquivalentIntegrationsMod.instance, GUI_ID, worldIn, pos.getX(), pos.getY(), pos.getZ());

        return true;
    }
}